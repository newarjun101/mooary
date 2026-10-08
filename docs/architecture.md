# Architecture

## The shape of it

```
ui/            Compose screens + per-screen state holders (no framework ViewModels)
  ├── navigation/   typed routes, EditorTarget
  └── base/         SessionHolder, shared UI helpers
domain/         models, repository interfaces, use cases — pure Kotlin
data/
  ├── local/        SQLDelight database and queries
  ├── remote/       REST data sources, Firebase clients, offline stand-ins
  ├── repository/   Default*Repository: single source of truth, maps rows <-> models
  ├── sync/         SyncManager
  └── export/       platform export contract
core/            logging, errors, datetime, validation, notifications, platform contracts
di/              Koin modules
```

Dependencies point inwards. `domain` imports nothing from `data` or `ui`; `data` never
imports a Compose type; `ui` talks to use cases and repository flows only. The only
`actual`/`expect` pairs are in `core/platform` and `core/notifications`, and each one has a
binding on every target — there is no "missing on this platform" branch to discover at
runtime.

## State: one source of truth, two stores

The local SQLDelight database is authoritative. Every repository read is a query against it,
exposed as a `Flow`, so the UI updates the instant a write lands — before any network call
has happened. Remote data is a *replica*, and the sync engine is what reconciles it.

That ordering is what makes the app work on a plane. Nothing in the write path requires a
network round-trip; writes fail loudly only when the *local* database rejects them.

## Sync protocol

`SyncManager.syncNow()` runs on a background dispatcher and, in order:

1. **Push deletions.** Rows in `PENDING_DELETE` are removed remotely first, so a re-created
   entry of the same id cannot be resurrected by an in-flight pull.
2. **Push upserts.** `PENDING_CREATE` and `PENDING_UPDATE` rows are written, then marked
   `SYNCED` on success. Failures leave the row pending and are retried next time; the
   manager never clears a pending state for a write it could not confirm.
3. **Push the profile**, if it changed.
4. **Pull remote changes.** Documents newer than the local row are merged in; equal or older
   documents are ignored, which makes a re-run of the same pull a no-op.

Each step is idempotent, so an interrupted sync is safe to repeat.

### Conflicts

| Local state | Remote state | Outcome |
| --- | --- | --- |
| `SYNCED` | newer | Remote wins; the local row is updated |
| `PENDING_*` | newer | Local wins; the change is pushed next run, and an anomaly is recorded |
| `PENDING_DELETE` | any | Deletion wins; the remote document is removed |

Pending local work is never discarded in favour of a remote document — the user typed it
more recently, and silently overwriting a journal entry is the one failure mode this app
must not have. `SyncAnomaly` records what happened so the profile screen can surface it
instead of the data vanishing without explanation.

## Per-platform bindings

| Contract | Android | iOS | Desktop |
| --- | --- | --- | --- |
| `SecureTokenStore` | `EncryptedSharedPreferences` with an AES256-GCM `MasterKey` | Keychain, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | in-memory, session-only |
| `NotificationManager` | `AlarmManager.setWindow` + `BroadcastReceiver` | `UNUserNotificationCenter` repeating calendar triggers | `UnavailableNotificationManager`, reported as unsupported |
| `FileExporter` | `cacheDir/exports`, shared through `FileProvider` | `Documents/Diary Exports/`, excluded from backup | `~/Diary Exports/`, opened with the system handler |
| `loadFirebaseConfig()` | `res/raw/firebase_config.json` | `FirebaseConfig.plist` in the bundle | env vars or `firebase.properties` |
| Coroutine context | `Dispatchers.IO` for disk, `Default` for CPU work | `Dispatchers.Default` only — there is no `IO` on native | `Dispatchers.IO` |

Three of those deserve a note.

**`setWindow` over `setExactAndAllowWhileIdle`.** A reminder that arrives within fifteen
minutes of 21:00 is as useful as one that arrives at 21:00 sharp. Exact alarms cost
battery, and since Android 12 they require a permission the user has to grant. The
trade-off is deliberate.

**Android re-arms each reminder, because `AlarmManager` cannot repeat one.** There is no
repeating *weekday* trigger: `setRepeating` is a fixed interval, which drifts by an hour
across a daylight-saving change and cannot express "Mondays at all". So the alarm is
one-shot and `ReminderReceiver` arms the next occurrence after each delivery, from the hour,
minute and day set carried in the intent. Both ends call `scheduleReminderAlarm`, so the
receiver cannot compute the next fire time differently from the scheduler, and the
re-arming time comes from the shared `ReminderSchedule` — the alternative, a fixed 24-hour
step from the last delivery, would drift.

**Weekly reminders on iOS are one request per weekday.** A repeating
`UNCalendarNotificationTrigger` can only ever fire on a single weekday, so a set like
{Monday, Wednesday} becomes two requests. Their identifiers are derived from the reminder
id (`<id>-1` … `<id>-7`), which is what makes `cancelReminder` able to remove all of them
without enumerating pending requests — iOS does not support that.

**Daily-versus-weekly is decided once, in shared code.** Both schedulers need to know
whether a request repeats every day or on a particular set, and the obvious test — "are all
these values in 1..7?" — is true for *every* weekly set, which quietly turns a
once-a-week reminder into a daily one. `ReminderSchedule.repeatsDaily()` compares against
the full seven-day set instead, and is covered by tests. A set of only out-of-range values
is not daily either: it is unschedulable, and must not be expanded into a reminder nobody
asked for.

**The preference is not the schedule.** `AppSettings.remindersEnabled` records intent;
`SyncReminderScheduleUseCase` is what arms alarms, and the profile switch calls it rather
than writing the setting directly. These were briefly separate, and the result was a
reminders switch that turned on, stayed on, and armed nothing. The use case returns a
`ReminderOutcome` so the UI can say what actually happened — `PERMISSION_DENIED` when the
user declines the system prompt, `UNSUPPORTED` on desktop — instead of leaving a switch
that claims to be on while nothing is scheduled. Ids are derived from the reminder kind, so
applying the preference repeatedly replaces alarms rather than stacking them.

**Permission is requested from the UI, not the scheduler.** On Android 13+ `POST_NOTIFICATIONS`
is granted from an Activity, and on iOS the prompt is presented at most once, so neither can
be obtained by the background-capable `NotificationManager`. `rememberNotificationPermissionRequester`
is the seam: an Activity-backed launcher on Android, `UNUserNotificationCenter` on iOS, and
an honest `false` on desktop. The manager can only *check* permission, and the use case
re-checks it before arming, because the user can revoke it after the fact.

**Alarms are re-armed on every visit to the profile screen.** Android clears alarms on
reboot, and app updates and force-stops clear them too, while the preference survives all
three.

**Neither platform can list its own schedule.** Android's alarm table and iOS's pending
request list are both write-only from the app's point of view. `scheduled()` therefore
returns an empty list rather than a fabricated one, and a test on that returning a guess
would be a test that only proves the guess.

## Localisation

The domain never holds display text. `MoodDescriptor` carries a `nameKey`, emotion group
keys are `emotion_group_*`, and emotion ids are stable strings, so the presentation layer
decides what each one reads as. `ui/labels/CatalogLabels.kt` holds the key-to-resource
mapping and the `moodLabel` / `emotionLabel` / `emotionGroupName` helpers every screen uses.

The mapping is a hand-written table rather than a generated one because Compose resources
are resolved at compile time: a key cannot be looked up by name at runtime, so a new mood
has to be added to the table to be renderable at all. That friction is the point, and
`CatalogLabelsTest` asserts the table and the catalogue agree in both directions, with no
orphans on either side.

Two consequences worth knowing:

- **`tired`, `sad`, `angry` and `anxious` are both moods and emotions.** They share ids but
  are prefixed `mood_` and `emotion_` respectively, and a test asserts they never resolve
  to the same resource.
- **Compose resources have no `<plurals>`.** Counts are phrased to read correctly at any
  value rather than assembled as a singular form plus a trailing `s`.

`MoodDescriptor.descriptionKey` is declared and mapped to nothing: no screen renders mood
descriptions yet, so inventing copy for them would be dead weight.

## Navigation and the editor hand-off

Navigation is `androidx.navigation` in common code, with typed destinations
(`Destination.Home`, `Destination.Editor`, …). A `topLevel` destination set marks the
bottom-bar roots; the back arrow is shown for everything else, because the multiplatform
navigation API used here does not expose a reliable "can I pop" signal.

Selecting an entry to edit happens through `EditorTarget`, a single-slot `MutableStateFlow`
owned by Koin. The list screen writes the id; `DiaryApp` collects it *once* inside
`remember` and navigates. Passing the id as a navigation argument instead would re-trigger
navigation on every recomposition of the destination.

## Time, time zones and dates

`entryDate` is a calendar day in the user's local zone; every stored timestamp is UTC.
`TimeProvider` is the only clock the domain can see, which is what makes streak and
statistics tests deterministic. `kotlin.time.Instant` and `kotlin.time.Clock` are used
throughout — the deprecated `java.time`-based kotlinx APIs are avoided so the same code
compiles for native.

`ReminderSchedule.nextTriggerAt` is shared by both native schedulers, which is why a
weekday means the same thing on Android and iOS, and why the daylight-saving case is covered
by a test rather than by hope.

## Validation in three places

`core/validation/Limits.kt` defines every size limit once. The same numbers appear in:

1. `Validators` — immediate feedback in the form.
2. The repositories — a write larger than the limit never reaches the database.
3. `firestore.rules` — the server refuses it even if a client bypasses the app.

## What is deliberately not here

- **No official Firebase KMP SDK.** The REST clients keep one implementation for three
  targets. The cost is hand-written error mapping; the benefit is no platform-specific
  initialisation dance.
- **No offline write queue table.** `sync_status` on the row itself is the queue, so a
  pending write is visible in the same row the user is editing and cannot be orphaned.
- **No DI framework abstraction in the domain.** Use cases take constructor parameters.
  Koin is used to wire them, and nothing in `domain` knows Koin exists.
