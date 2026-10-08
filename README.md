# Diary & Mood Tracker

A private journal with mood tracking, built with Kotlin Multiplatform. One shared codebase
runs on Android, iOS and desktop; everything that genuinely differs (secure storage,
reminders, file export, Firebase config) has a per-platform binding.

Entries are written to a local SQLDelight database first, so the app works with no network
at all. Sync is opt-in: a signed-in account mirrors the local database to Firestore, and a
build without Firebase configuration stays entirely local.

## Features

- **Diary** — create, edit, delete, restore, favourite, tag, search, and attach a mood,
  emotions, location and weather to each entry. Drafts are persisted, so a crash or a
  background kill loses nothing.
- **Mood** — several check-ins per day, each with a note and emotions. Daily, weekly and
  monthly analytics: averages, distribution, best and worst days, and the most common
  emotions.
- **Calendar** — month grid with per-day activity, plus the streak and the trailing
  twelve-month heat map.
- **Home** — today's summary, current streak, recent entries and a quick mood check-in.
- **Profile** — theme, reminder schedule, export, sync status, account and sign-out.
- **Reminders** — `AlarmManager` on Android, `UNUserNotificationCenter` on iOS. Daily or on
  chosen weekdays.
- **Export** — JSON (pretty-printed, versioned) and RFC-4180 CSV, written to the platform's
  documents directory and shared through the system sheet.
- **Offline-first sync** — the local database is the source of truth; a `SyncManager` pushes
  pending changes, pulls remote updates and resolves conflicts, recording anomalies rather
  than losing data.

## Stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.4.10 |
| UI | Compose Multiplatform 1.12.1, Material 3 |
| Architecture | MVVM + unidirectional data flow, hand-rolled state holders |
| DI | Koin 4.2.1 |
| Local storage | SQLDelight 2.4.0 (SQLite) |
| Networking | Ktor 3.5.0 |
| Serialization | kotlinx.serialization |
| Backend | Firebase REST (Firestore + Identity Toolkit) |
| Tests | kotlin.test, kotlinx-coroutines-test |

Firebase is reached over its REST API rather than an official KMP SDK, so one client
implementation serves all three targets.

## Project layout

```
composeApp/          shared module — all targets
  commonMain/        domain, data, sync, platform contracts, UI
  androidMain/       Android bindings (driver, alarms, encrypted store, config)
  iosMain/           iOS bindings (Keychain, UNUserNotificationCenter, exporter, config)
  desktopMain/       desktop bindings (file exporter, config from properties)
  commonTest/        unit tests for the pure logic
androidApp/          Android application module (Application, Activity, BroadcastReceiver)
firestore.rules      security rules
firestore.indexes.json
firebase.json        deploy + emulator configuration
```

## Building

```bash
./gradlew :composeApp:desktopTest                  # run the unit tests on the JVM
./gradlew :composeApp:testAndroidHostTest          # run the same tests on the Android host
./gradlew :composeApp:compileKotlinDesktop         # compile the desktop target
./gradlew :androidApp:assembleDebug                # build the Android APK
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:run                          # run the desktop app
```

Requirements: JDK 17 or newer (the bytecode target is 17), the Android SDK (compileSdk 37,
minSdk 24), and — for iOS — Xcode with the Kotlin/Native toolchain, which Gradle downloads on
first use.

`iosX64` is disabled: it is not supported by the current Compose Multiplatform release, and
the iOS Simulator is always arm64 on Apple Silicon.

## Firebase configuration

The app is fully functional without any Firebase configuration. With none, it runs in a
local-only mode under the synthetic user id `local_user`, and the sign-in screen is the
only place a backend is ever needed.

To enable sync, supply a configuration through the mechanism native to each target. The
three keys required everywhere are the API key, the project id and the app id.

**Android** — put `google-services.json` in the repository root. The
`generateFirebaseConfig` task converts it to `res/raw/firebase_config.json` during the build;
it has to be converted, because the console nests apps under a `client` *array* while the
app reads a `client` *object*, so an unmodified copy would load nothing and start offline in
silence. To hand-write the resource instead, see the guide. These keys are optional
overrides: `firestore_base_url`, `identity_base_url`, `secure_token_base_url`, `emulator`.

**iOS** — `FirebaseConfig.plist` in the app bundle, with `API_KEY`, `PROJECT_ID` and
`APP_ID`, optionally `AUTH_DOMAIN`, `GCM_SENDER_ID`, `STORAGE_BUCKET`, `FIRESTORE_BASE_URL`,
`IDENTITY_BASE_URL`, `SECURE_TOKEN_BASE_URL` and `EMULATOR` (the last as the integer `1`).

**Desktop** — environment variables, or a `firebase.properties` file in
`~/.diary-app/`: `DIARY_FIREBASE_API_KEY`, `DIARY_FIREBASE_PROJECT_ID`,
`DIARY_FIREBASE_APP_ID`, and optionally `DIARY_FIREBASE_SENDER_ID`,
`DIARY_FIREBASE_STORAGE_BUCKET`, `DIARY_FIREBASE_EMULATOR`,
`DIARY_FIRESTORE_EMULATOR_HOST` and `DIARY_AUTH_EMULATOR_HOST`.

A Firebase API key identifies the project; it is not a secret. Authorisation is enforced by
Firestore security rules and ID tokens, which is why `firestore.rules` grants access only
on a `users/{request.auth.uid}/...` path. Ownership comes from the path and not from a
document field, so a client cannot claim a user id it does not own; the rules then check
the *shape* of each document against the fields `RemoteMappers` writes, with `hasOnly` so
no extra fields can be smuggled in.

`firestore.indexes.json` is intentionally empty. The only remote query the app issues is
`where updatedAt > watermark, orderBy updatedAt`, on both `diaryEntries` and `moods`, and
Firestore serves that from its automatic single-field indexes. Composite indexes would cost
write quota and buy nothing.

**[`docs/firebase-setup.md`](docs/firebase-setup.md) is the full guide** — project creation,
API enablement, per-platform setup, the emulator, the auth flow, and troubleshooting. The
summary above is enough to make a build work; the guide explains what each piece is for.

### Local emulators

```bash
firebase emulators:start          # auth on 9099, firestore on 8080, UI on 4000
```

Set the emulator flag in the platform configuration — `DIARY_FIREBASE_EMULATOR=true` on
desktop, `-Pdiary.firebaseEmulator=true` on Android — to point the app at them. Keep the
project id consistent with the emulated one, since the Firestore emulator stores data per
project id.

`firebase emulators:exec` will *not* catch a broken rules file — it starts happily and only
fails when a request is evaluated. To check the rules, start the emulator and make a
request, or deploy to a scratch project.

### Deploying the backend

```bash
cp .firebaserc.example .firebaserc   # then set your project id
firebase use --add                    # or edit .firebaserc by hand
firebase deploy --only firestore:rules,firestore:indexes
```

## Testing

```bash
./gradlew :composeApp:desktopTest :composeApp:testAndroidHostTest :composeApp:iosSimulatorArm64Test
```

The same 96 tests run on all three platforms, which is the point: a suite that only ever
compiles for the JVM will happily accept test names and helpers Kotlin/Native rejects. The
tests concentrate on the logic that is both hard to get right and cheap to verify:
validation limits, word counting and search, streak arithmetic across gaps and misdated
rows, mood aggregation with tie-breaking and empty periods, RFC-4180 escaping, and
next-fire-time computation across a daylight-saving transition.

The reminder tests exist because the reminder feature had three separate defects that
compiling could not catch and that no user report would have pinpointed: a weekly reminder
fired every day, Android reminders fired exactly once, and the reminders switch armed
nothing at all because the preference was never handed to the scheduler.

Platform bindings — the SQLDelight driver, Keychain, alarms, notification centre — are
verified by compiling and running on the device, not by mocks that would only prove the
mock works.

## Design notes

See [docs/architecture.md](docs/architecture.md) for the layering, the sync protocol, the
conflict rules and the decisions behind the per-platform bindings.

## Privacy

Diary text never reaches a log sink: `AppLogger` calls take a tag and a lambda, and the
production logger is redacted. Tokens are stored in the Android Keystore via
`EncryptedSharedPreferences` and in the iOS Keychain; desktop keeps them in memory and
signs the user out when the process ends, which is the safe direction to fail. The database
lives in a no-backup directory on Android and exports are excluded from iCloud on iOS.
