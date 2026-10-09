# AGENTS.md

Working notes for coding agents on this repository. Read this before changing anything.

This is a Compose Multiplatform diary and mood tracker. Kotlin is the only language; the UI is
shared across three platforms. Full design rationale lives in `docs/architecture.md`, setup
instructions in `README.md`, and backend setup in `docs/firebase-setup.md`. This file covers
what you cannot infer quickly from the code — the traps that build cleanly and then fail, and
the invariants that must survive your change.

## Layout

| Path | Contents |
| --- | --- |
| `composeApp/src/commonMain` | All shared business logic and UI |
| `composeApp/src/{androidMain,iosMain,desktopMain}` | `actual` platform bindings only |
| `composeApp/src/commonTest` | Shared tests |
| `composeApp/src/desktopTest` | JVM-only tests (resource/locale assertions live here) |
| `composeApp/src/commonMain/kotlin/com/arjun/soul_note/ui/settings` | Settings: profile, password, account actions |
| `composeApp/src/commonMain/kotlin/com/arjun/soul_note/ui/profile` | Read-only profile summary; links to settings |
| `composeApp/src/commonMain/sqldelight` | `.sq` schema and queries |
| `androidApp` | Android host: manifest, launcher label, resource staging, signing |
| `iosApp` | iOS host: `project.yml` (source of truth), generated `.xcodeproj`, Swift entry, Firebase config |

Two Gradle modules only: `:composeApp` and `:androidApp`. Package `com.arjun.soul_note`.

Versions are pinned in `gradle/libs.versions.toml` — Kotlin 2.4.10, Compose Multiplatform
1.12.1, AGP 9.2.1, SQLDelight 2.4.0, Ktor 3.5.0, Koin 4.2.1. Do not bump one without the
others; Compose and AGP are tightly coupled here.

## Build and verify

```bash
# Fast inner loop
./gradlew :composeApp:compileKotlinDesktop

# Everything. Run this before you call any change done.
./gradlew :composeApp:desktopTest :composeApp:testAndroidHostTest \
  :composeApp:iosSimulatorArm64Test :androidApp:assembleDebug \
  :androidApp:assembleRelease :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  --rerun-tasks --console=plain
```

Test totals: 306 desktop, 258 Android host, 258 iOS Simulator. All three must pass — a
`commonTest` that only runs on desktop has not been verified on the other two.

The iOS app itself is verified by building and launching `iosApp/iosApp.xcodeproj` — see
`iosApp/README.md`. Screenshots cannot be viewed from this environment, so a launch check means
zero exceptions in the console plus a pixel histogram of the screenshot that shows the paper
palette rather than one flat colour.

On-device checks use `emulator-5554`, package `com.arjun.soul_note.debug`, entry
`com.arjun.soul_note.MainActivity`:

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell cmd locale set-app-locales com.arjun.soul_note.debug --locales my-MM
adb exec-out uiautomator dump /dev/tty
```

Parse `uiautomator` output to assert layout — screenshots cannot be viewed in this
environment, so the XML dump is the only way to check what actually rendered. There is no
Compose UI-test harness; do not add one casually.

## Traps

These are the failure modes that compile, build, and install without complaint.

**Compose resources on Android need manual staging.** The Compose Gradle plugin only wires
`.cvr` resources into APK assets for `com.android.library`. This project consumes
`:composeApp` through AGP 9's `com.android.kotlin.multiplatform.library`, where the wiring
never registers — no `assembleAndroidMainResources` task, assets stay empty. Nothing reports
it: the app launches and dies on the first `stringResource` call. `StageComposeResources` in
`androidApp/build.gradle.kts` exists to fix this and asserts non-empty output, so do not
delete or weaken it.

**Renaming the resource package is a two-place edit.** `packageOfResClass` in
`composeApp/build.gradle.kts` and `composeResourcePackage` in `androidApp/build.gradle.kts`
must agree. Change one and the build succeeds, then the resource lookup path misses at
runtime. The `check` in `StageComposeResources` catches the renamed case; keep it.

**Renaming the app package is an eight-place edit.** Directory roots (`com/example/diary` →
`com/arjun/soul_note` in all eight source sets including `sqldelight`), every `package` and
`import`, plus `applicationId`, `namespace`, `bundleId`, SQLDelight `packageName`, desktop
`mainClass`, ProGuard rules, and `docs/firebase-setup.md`. A blanket find-and-replace over
`com.example.diary` covers all of it. Then confirm the built identity:

```bash
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging \
  androidApp/build/outputs/apk/debug/androidApp-debug.apk | grep -E '^package|application-label'
```

**`stringResource` does not collapse `%%`.** Unlike `String.format`, Compose's own substitution
leaves it as a literal `%%`, so a percentage renders as `25%%`. Use a single `%`. This shipped
once in `chart_mood_share`.

**SQLDelight's generated `Schema.create` issues bare `CREATE TABLE`.** On Desktop the driver
must reconcile `PRAGMA user_version` before creating, or the app dies in Koin on the *second*
launch with "table diary_entries already exists". See `applySchema` in
`DatabaseDriverFactory.desktop.kt`. It also stamps the legacy unversioned file, since an older
build created the tables without recording a version.

**`MaterialTheme` does not set `LocalContentColor`, so an uncoloured `Text` draws black.** The
default is `Color.Black`, and only Material containers (`Surface`, `Button`, `Card`, `TextField`,
`AlertDialog`) push a value inward. A `Text` with no `color` sitting in a plain `Box`/`Column`
therefore rendered pure black in *both* themes -- indistinguishable from ink on paper in light,
unreadable on espresso in dark. That asymmetry is what hid it: light mode looked perfect, and
every label inside a card was already correct, so the visible offenders were only the empty-state
titles (`States.kt`, `dashboard_empty_title`), which `seedDemoData` keeps off-screen in debug.
`DiaryTheme` now seeds `LocalContentColor` with `onSurface` so "no explicit color" means "themed";
do not remove that provider, and do not add `Surface` wrappers to paper to work around it.

To audit this class of bug without a UI-test harness: dump the accessibility tree, then for a
text node's bounds count exact colours in the screenshot. A node whose *modal* pixel is
`(0,0,0)` with several hundred identical pixels is black glyphs -- for a large bold title the
glyphs outnumber the page, so the modal is the ink, not the background, and a naive
"modal = background" reading inverts the result. Before/after on the empty-search state is a
reliable pair: same node, same glyph-pixel count, `(0,0,0)` -> `(239,228,211)`.

**iOS applies every system inset twice unless the host goes edge-to-edge.** SwiftUI lays out a
child inside the safe area by default, so `ComposeView` arrived already inset while Compose also
applied `WindowInsets` itself — each inset counted twice, and nothing errors. On an iPhone 16 Pro
the app bar's first content sat at 134pt against a 59pt safe-area edge (59 + 59 + the app bar's
own 16pt), and the `NavigationBar` stopped at 840pt, exactly `874 - 34`, so the home-indicator
inset was being paid as blank space *inside* the bar while 34pt of bare page sat below it. That is
the "terrible padding" this presents as; it is invisible in code review because
`.ignoresSafeArea(.keyboard)` looks like it is already handling this, and it does handle the
keyboard. The fix is `.ignoresSafeArea()` with no argument on the `WindowGroup` content, which
hands the insets to Compose and applies them once — the same layout Android gets. Verified by
measurement, not by eye: first app ink 150pt -> 88pt (a 64dp bar at 59 + 32), and the nav bar
760..874pt instead of 728..840pt. Do not "fix" this by removing Compose's inset handling instead;
that trades a doubled inset for content under the status bar and the home indicator.

**iOS paints its safe-area bands with SwiftUI's scene root, not `UIWindow`.** The strips above and
below the page are drawn by whatever covers the window, and the default is opaque white. On a warm
paper palette that reads as two hard white stripes down the length of the app, and it is the single
most visible difference between this app on iOS and on Android. Setting `window.backgroundColor`
changed nothing measurable: the scene's root view already covers the window and carries its own
background. The fix is a background on the `WindowGroup` content, which is why the whole thing
hangs off `iosApp.swift` rather than off anything in Kotlin. Since the host now goes edge-to-edge
that background sits *behind* Compose's page rather than in front of it, and is kept only so a
window that is briefly page-coloured beats a white flash before the first frame. `Paper` and
`Espresso` are therefore duplicated there as RGB literals — the duplication is real and is held in
place by `PaperInkMatchesThemeTest`, which asserts on the *set* of colours in the definition so
that a deleted dark branch fails rather than passing.

**A lift toward white is a no-op when the base is already white.** `paperCard` lifts its fill
55% toward white so a card separates from the page. The base has to be the *page* colour for
that to mean anything: defaulting it to `surfaceContainerLowest` (hard-coded `#FFFFFF` in the
light scheme) lerps white to white, and every card renders as a cold white rectangle -- the one
neutral in the light palette carrying neither the red nor the yellow every other neutral keeps.
The three fill defaults in `Surface.kt` are `colorScheme.surface` for this reason. Symmetrically,
`surfaceContainerLowest` itself is now `PaperBright` rather than `#FFFFFF`, because the range
pills and nav bar sit on it and were going cold for the same reason. A screenshot histogram
catches this instantly: pure white went 1,508,268 px -> 583.

**A low-chroma slice is not a missing slice.** `Mood.NEUTRAL`'s accent is `#8E8E93`, a grey
whose channel spread is 5. Any wedge detector keyed on saturation reports it as a hole in the
pie, and the 46.8-degree "gap" that produces looks exactly like a slice that failed to draw.
Match against the palette (including the 0.42 alpha dim over the card fill), not against "is
this colourful". Same class of error as the modal-pixel trap above: the measurement, not the
chart, was wrong.

**A drawn dimension in raw pixels is a different chart on every screen.** The pie's selection
pop was `7f` in a `DrawScope`, where every number is px, while `diameter` is `168.dp`. On mdpi
that pop is 4% of the pie; on a 420dpi phone it is 1.6% and the slice barely moves, so the
caption becomes the only evidence a selection happened. `7.dp.toPx()` fixes it. The 4dp rim
inset had the same bug in both the draw path *and* the hit-test, which is worse: a tap radius
that disagrees with the drawn radius accepts taps on bare page and drops taps on the rim.
`MoodPieGeometryTest` pins the pop as a share of the pie across four densities so it cannot
quietly regress.

**A `remember` keyed on a collection's size cannot tell one set from another.** The selection
was `remember(distribution.size)`, on the theory that a range change drops a mood. Two ranges
can hold the same eight moods and be entirely different ones, and then index 3 stays selected
and the caption names a mood the user never tapped. Keyed on `distribution.map { it.mood }`
instead, which also covers the size-changing case. A count is a proxy for identity, and a
proxy is exactly what fails on the case nobody tested.

**A `pointerInput` block that reads state it did not capture as a key sees a stale value.**
Toggling a selected slice off did nothing, and the cause was not the tap: `detectTapGestures`
only re-runs its block when the `pointerInput` key changes, so `hit == selected` compared
against the selection *as of the composition that built the detector* -- forever `-1`, because
the key was the extents and those never changed. Every tap therefore re-selected rather than
cleared. Fixed by reading `selectedIndexState.value` inside the handler instead of a captured
`val`, which needs no key change to stay current. Proven by building the reverted version and
tapping: `25%, 25%, 25%` before, `25%, 16, 25%, 16` after. Two more traps hid behind this one
while probing it: `uiautomator` clips `bounds` to the viewport, so a pie scrolled past the fold
reports a centre ~120px above where it is drawn and every "tap" lands on the rim; and the
overlay's own text nodes sit near the true centre, so deriving taps from the clipped box taps
the edge. Fixing a gesture means re-deriving the geometry immediately before each tap.

**A full-height accent bar needs `IntrinsicSize.Min` on the row, not on the bar.** A
`Box(Modifier.width(4.dp).fillMaxHeight())` inside a plain `Row` is measured first and
resolves against an unbounded height, so it silently renders at zero pixels. Nothing errors
and nothing is clipped off-screen -- the spine is simply absent. The pairing that works is
`Row(Modifier.height(IntrinsicSize.Min))` on the *parent* and `fillMaxHeight()` on the child.
Putting `IntrinsicSize.Min` on the child instead is the same bug wearing a different hat.

**A Compose string needs an explicit import, and the failure lies about why.** The accessors
are generated as top-level `internal` extension properties, so a new key has to be added to both
`strings.xml` files *and* imported as `com.arjun.soul_note.resources.<key>`. Omit the import and
`Res.string.<key>` reports `Unresolved reference` while the generated `String2.commonMain.kt`
visibly declares it, the `.cvr` contains it, and the accessor task has run. The declaration is
really there; the *reference* is not in scope, and nothing in the error says so. A clean build, a
restarted Kotlin daemon and `--no-build-cache` all reproduce it, so none of those are worth trying.

**A table whose writer is named `upsert` must actually `INSERT OR REPLACE`.** `user_profiles`
was a bare `INSERT` while `diary_entries` and `mood_entries` had already been fixed, so the
first sync pull — which re-applies the profile that sign-up already stored — died on
`SQLITE_CONSTRAINT_PRIMARYKEY` and aborted the whole pass, leaving the entry pending with no
visible cause. Nothing references `user_profiles` by foreign key, so `OR REPLACE` is safe there.
`LocalStoreUpsertTest` covers it.

**Navigating on the tap cancels the coroutine doing the work.** "Delete account and all data"
used to call `onSignOut()` in the same click handler that started the deletion. Popping the
route clears the view model, `onCleared` cancels its `CoroutineScope`, and the delete dies
mid-flight with nothing logged and nothing on screen. Navigate on a state flag the view model
sets on success (`ProfileState.deleted`), never on the tap. The same screen also carried an
`error` field that `ProfileScreen` never rendered, so every failed action there was silent —
`AuthScreen` and `DiaryEditorScreen` both render theirs, and any new screen must too.

**A screen that fires a coroutine must not navigate until it reports back.** See the entry
above. The general shape: `onCleared` cancels the scope, so anything async has to complete
before the route is popped, or its result is unreachable.

**A use case that is plumbed but never registered in Koin is unreachable.** `changePassword`
had a client method, a data source, a repository method and a use case, all reachable by
nothing — there was no factory in `AppModule`, so `get()` had no bean. It compiled, every
test that touched the use case passed, and the only symptom was that the first screen to ask
for it crashed on open with a bare `InstanceCreationException`. Wiring a use case means adding
the factory, and DI is not covered by the unit tests.

**Reading the on-device database means copying the WAL too.** Pulling only `no_backup/diary.db`
off the device reports a table as empty when it is not: SQLDelight runs in WAL mode and recent
commits sit in `diary.db-wal` until a checkpoint. Copy `diary.db`, `diary.db-wal` and
`diary.db-shm` together, or the check reports a false negative.

**An unweighted child in a `Row` claims the whole width.** `Modifier.fillMaxSize()` on a
sibling pushes the next child off-screen without any error — the navigation rail vanished
entirely in wide layouts because of exactly this. Use `Modifier.weight(1f)`.

**Firebase config failures are silent.** `google-services.json` lives at the repository root,
is git-ignored, and is converted by `generateFirebaseConfig` into `res/raw/firebase_config.json`.
The console nests apps under `client` as a JSON **array** while the app reads a `client`
**object**, so the raw file parses and loads nothing. Every failure path logs and continues,
so the app just starts in offline mode. The confirming log line is
`Generated Firebase config for project '<id>'`. If it is absent, the build succeeded and
Firebase is off.

**Gating the messaging *service* does not gate the messaging *token*.** `-Pdiary.firebaseMessaging`
reaches `:androidApp` as a manifest placeholder, so a default build declares
`FcmMessagingService` as `android:enabled="false"` — and still calls `getToken()` on every
sign-in, because the token is fetched by shared code, not by the service. Both halves looked
correct in isolation. `FirebaseMessaging.getInstance()` and `isAutoInitEnabled` answer perfectly
happily in a build with the service disabled, so the source code's own reasoning — detect the
flag by *using* the SDK, since only the device knows what it managed to do — could never
distinguish a default build from a flag-on one. The observable cost is a network round trip and
a device document per signed-in user in the build that is supposed to not have the feature.
The flag is therefore compiled in as `BuildFlags.FCM_ENABLED`, generated by `generateBuildFlags`
into `commonMain`, and checked before the SDK is touched. A runtime probe is the wrong tool
here: the decision is a property of the build, not of the device.

**A new Firestore collection with no `match` arm is refused, and nothing says so.** The
`register-device` collection that push registration writes to was added with no arm in
`firestore.rules`, so it fell through to the deny-all catch-all at the bottom of the file. That
is the correct default -- a rule that quietly allows unknown collections is how a diary app
becomes world-readable -- but it interacts badly with registration being fired and forgotten by
design: every write came back 403, nothing was logged as an error, and no push ever arrived.
The failure is indistinguishable from "messaging is off on the project".

Verify new write paths against the emulator with two real accounts rather than one. Note that a
partial `PATCH` with an `updateMask` *merges*, so testing a missing field against an existing
document passes and proves nothing -- use a fresh document id. Refusals come back **403**, not
400, because the REST API reports a failed rules check as `PERMISSION_DENIED`. And the emulator
ignores a plain `Authorization: Bearer <uid>` header as `invalid jwt`; it only accepts a real id
token from the auth emulator, or `Bearer owner`, which bypasses rules entirely and therefore
tests nothing. Start `--only auth,firestore`, sign up twice, and keep the tokens.

**A per-app locale override is keyed by package.** After a package rename, `my-MM` set on the
old id does not apply and the app silently falls back to English. Re-set it on the new id.

**A DI registration that resolves a client the local-only build cannot build kills the app at
first composition.** `FirestoreClient` throws on purpose when there is no config, so a
local-only build never opens a connection -- but `single<PushRegistrar>` passed `firestore =
get()` into `createPushRegistrar`, forcing that throw on the one platform that could never use
the client anyway (iOS returns `null` from `createPushTokenSource()` unconditionally, so its
registrar is always the unsupported one). iOS therefore died inside Koin before its first
frame, and it was invisible on desktop and Android: desktop only hits the offline branch
without `~/.diary-app/firebase.properties`, and Android's `generateFirebaseConfig` always
writes *something*, real or demo. `createPushRegistrar` now takes `FirestoreClient?` and the
registration resolves one only when `RemoteConfig.isConfigured`. **The lesson is broader than
push: a `single` whose factory `error()`s on absent config is only safe if nothing resolves it
unconditionally.** `AppModuleLocalOnlyTest` pins this by overriding `RemoteConfig(null)` --
overriding rather than relying on the machine having no config, because a developer machine
that *does* have config is exactly how this hides.

**An empty build setting overrides its own xcconfig, so "empty by default" is not the same as
"absent".** XcodeGen writes `baseConfigurationReference` and `settings.base` into the *same*
`XCBuildConfiguration`, and a build setting beats that configuration's base xcconfig.
`DEVELOPMENT_TEAM: ""` in `project.yml` therefore shadowed the team in the git-ignored
`Local.xcconfig` that `Configs/Debug.xcconfig` includes -- and the result is a device build
failing with `requires a development team` while the team sat in the file the README tells you
to put it in. This was worth a probe rather than a reason, because the precedence is the opposite
of the common belief: writing the empty default seemed like the safer, more explicit choice. The
key is now simply absent. The way to settle it is `DEVELOPMENT_TEAM` set to a deliberately bogus
value and checking whether the error changes -- `ZZZPROBE99` moved the failure from "requires a
development team" to "No profiles ... were found", which is the pair that proves the file is read.
Note also that `#include?` rather than `#include` is what lets a fresh clone build at all, since a
missing include is a parse error rather than an ignored setting.

**`CODE_SIGNING_ALLOWED = NO` for Debug is what makes a device build impossible while still
looking configured.** It used to be set in `project.yml` with Release overriding it back on. An
unsigned app installs on the simulator perfectly happily, so every simulator check stayed green and
the first sign of trouble was a device build refusing for reasons that never mentioned signing.
Automatic signing covers both destinations from one setting: a simulator build ad-hoc signs with
`-` and needs no team, so only `generic/platform=iOS` demands one -- which is the right moment to be
told so.

**Compose refuses to start on iPhone without a plist key it invented the name of.** With no
`CADisableMinimumFrameDurationOnPhone` in `Info.plist`, Compose Multiplatform throws
`IllegalStateException` from a background dispatch queue during the first composition: black
window, and the only clue is in the console. It is set in `iosApp/project.yml`. The equivalent
of the black-`Text` trap -- a default that is `Color.Black` in one theme and invisible in the
other -- except the whole app is the wrong colour.

**A `PRAGMA` that returns rows cannot go through `driver.execute`.** Both native factories have
this and had to be fixed independently: Android's routes `execute` to `SQLiteDatabase.execSQL`,
iOS's to SQLiter's update/delete path, and both refuse a row-returning statement with
"Queries can be performed using SQLiteDatabase query or rawQuery methods only". `PRAGMA
foreign_keys = ON` returns none and passes; `PRAGMA journal_mode = WAL` returns one row
("wal") and throws. Correct SQL, compiles, fails only on device. `driver.executeQuery` with a
mapper that consumes and discards the row is the fix.

## Invariants

- **Never log diary content, credentials, or tokens.** Not at debug level, not in error
  messages, not in analytics. See `core/logging/`.
- **`MaterialTheme` is not a content-colour provider.** An uncoloured `Text` must still read as
  themed; that is `DiaryTheme`'s `LocalContentColor` seed, not a per-call-site `color=`.
- **SQLDelight is the source of truth.** The network is an enhancement, not a prerequisite.
  The app must be fully functional with no backend. Never gate a local read on sync state.
- **No `SCHEDULE_EXACT_ALARM`.** Reminders use inexact scheduling; do not add the permission.
- **Use `kotlin.time`, not `java.time`,** in shared code. The Android and iOS bindings wrap
  it in `core/datetime/`.
- **Keep `BaseViewModel` as the single ViewModel base** and `rememberViewModel` bound to it.
- **Offline-first means writes are local and immediate.** Do not introduce a path that awaits
  the network before showing a result.
- **Do not enable Firebase APIs, create resources, or deploy rules** without explicit approval.
  They are externally gated and the developer may not have console access.

## Conventions

**Comments explain why, not what.** The house style is a short comment above non-obvious code
recording the constraint that forces it. Match the density of the surrounding file — several
files here carry a paragraph explaining a subtle build interaction, and that is intentional,
not noise. Do not add comments restating what the code does.

**Keep user-facing text in resources.** No literals in composables, including accessibility
content descriptions and chart labels. See "Localisation" below.

**No new dependencies without a stated reason.** The project runs without `material3-adaptive`
and without a charting library on purpose: breakpoints are hand-rolled and charts are drawn
with `Canvas`. Adding either is a real decision, not a convenience.

The one exception so far is `io.coil-kt.coil3:coil-compose`, added for the profile photo:
decoding, downscaling and caching a camera-sized image by hand across three platforms is a
large, bug-prone job, and this was the stated reason. Only the Compose artifact is taken --
`coil-network-ktor3` deliberately is not, because the bytes are fetched by the app's own Ktor
client, which already owns the refreshing Firebase id token. Coil is handed a path and never a
network url; it requires a jvmTarget of 11 or newer, and the project is on 17.

**Tests name the behaviour, not the function.** Backtick names reading as a sentence, for
example `charts stay single column until expanded`.

## Localisation

Two locales, no in-app language picker — the app follows the system/per-app locale.

- `composeApp/src/commonMain/composeResources/values/strings.xml` (English, default)
- `composeApp/src/commonMain/composeResources/values-my/strings.xml` (Burmese)

Both must carry identical keys and identical `%n$` format specifiers. `BurmeseStringsTest` in
`composeApp/src/desktopTest` enforces key parity, specifier parity, non-blank values, and
flags Burmese text still identical to English.

When you add a string:

1. Add it to **both** files. A one-sided addition fails the parity test.
2. Genuinely translate it. Brand names, emoji, and pure pass-throughs go in
   `IDENTICAL_BY_DESIGN` in that test — that set is not a place to hide unfinished work.
3. Do not add a percent sign without reading the `%%` trap above.

Deliberate conventions, do not "fix" them without asking:

- **Latin digits in Burmese.** `%d` is not converted to Myanmar numerals; converting would
  require app-wide number localisation.
- **Monday and Sunday are both `တ`.** Grid position disambiguates.

`ReminderCopy.copyOrNull()` returns English and `FakeDiaryData` holds English diary text. Both
are intentional — preview/test paths and user-authored content respectively — not untranslated
UI.

## Design system

One direction: **warm editorial journal** -- paper and ink, earth pigments, serif for
anything read and sans for anything operated. Tokens live in `ui/theme/`:

| File | Holds |
| --- | --- |
| `Color.kt` | Light and dark schemes. Every neutral carries red and yellow so greys stay warm; accents are muted a step off full chroma so they read as brand colour rather than decoration |
| `Type.kt` | Serif display/headline/prose, sans for labels; `SectionLabelStyle` for section captions |
| `Theme.kt` | `DiaryShapes`, the `Spacing` scale, `Elevation`, `DiaryTheme` |
| `Surface.kt` | `paperCard`, `JournalCard`, `PaperSurface`, `accentFill`, `pressScale`, `luminanceIsDark` |

**Use `JournalCard` and `PaperCard`, not Material's `Card` or `Surface`.** They are drop-in
for the call sites already migrated, and the app has no stock `Card` left. Reaching for
`Surface` puts a flat tonal fill on a textured page, which is the one thing that reliably
undoes this look.

`PaperSurface` is a flat page with fixed-seed grain. It is mounted once in `DiaryApp`, behind
the navigation chrome. Screens get it for free -- do **not** add another one inside a screen.

**There are no gradients in this app.** Not a deliberate ration, an absence. A vertical wash, a
top-lit card fill, a fading top-edge highlight, three corner blooms and a two-hue accent were
all here; all are gone. What replaced them is flat colour plus shadow, and the honest reason is
that a gradient is a way of asking colour to do a job that shape should be doing. The blooms
were the only thing stopping the page reading as a fill, and the fading card edge was the only
thing making a card look lit rather than lit-by -- both now carried by the shadow stack and the
grain. Do not reintroduce one as a "small" accent. `accentGradient` is now `accentFill` and it
is a solid `primary`; the range pill uses `animateColorAsState` directly, which it could not
while it was a brush.

**The page is a flat field plus grain and fibres.** Fixed seed on purpose -- a speckle pattern
that reshuffles on every recomposition is a detail nobody can name but everybody reads as
something wrong, and it redraws 278 primitives every frame. The fibres are what make the result
read as paper rather than as a colour.

**Depth comes from a stack, not from a value.** `paperCard` layers a wide faint ambient shadow,
a tighter denser one, a flat fill lifted a little off the page colour, and a hairline border.
With the gradients gone the shadow is doing all of the work, so the fill has to stay lifted --
collapse it to the page colour and a card is a rectangle with a shadow, which is to say an
outline.

**`Elevation` is a three-step scale, not a value per call site.** `card`, `raised`,
`overlay`, and nothing else — ad-hoc values (3dp on one card, 5dp on the next, 8dp on a
dialog) are why a screen looks assembled rather than designed. Shadows are neutral black:
tinted to the paper they read as a brown smudge, so the warmth belongs in the fill.

**`pressScale` does not overshoot.** A bouncy spring is a toy; a product's press response
eases in over ~120ms and stops.

**Serif is `FontFamily.Serif`, the platform face.** No font is bundled: that would mean
shipping binaries, choosing a licence, and adding weight to each build, and the serif is
doing the work rather than a novelty personality.

`compose.animation` is a declared dependency. material3 only pulls in `animation-core`, so
`animateColorAsState` and `AnimatedContent` are absent without it, and transitions become hard
cuts. It is first-party Compose and the stated reason is motion; it is not a licence to add
`material3-adaptive` or a chart library.

## Responsive layout

Hand-rolled, in `composeApp/src/commonMain/kotlin/com/arjun/soul_note/ui/adaptive/`:

- Compact `<600dp` — `NavigationBar`, one column
- Medium `<840dp` — `NavigationRail`, two-column stat tiles
- Expanded `>=840dp` — `NavigationRail`, four stat tiles, charts in a 2×2 grid

Read the class with `currentWindowWidthClass()`; pick grid counts with `adaptiveColumns()`.
`ProvideWindowWidthClass` uses `BoxWithConstraints` so a resized desktop window relayouts live,
which the platform configuration would not.

Content is capped once, in the shell's `NavHost`: `ContentWidth.reading` (720dp) for prose and
lists, `ContentWidth.dashboard` (1080dp) for the dashboard. Do not add per-screen caps.

## Cloud Messaging

The one Firebase SDK in the project. Everything else Firebase-shaped is hand-rolled over REST
because there is an endpoint to call; push has none, since delivery depends on Google's transport
reaching the device. Android only — iOS needs APNs and an `iosApp/` project, and neither exists.

Build it with `-Pdiary.firebaseMessaging=true`. The flag is off by default, and off means off:
the service is `android:enabled="false"` and `createPushTokenSource()` returns `null` because the
SDK was never asked for a token. **Verify both configurations** — a green build with the flag on
proves nothing about the default build users actually get.

**A registration token is a credential, and this is where that rule gets tested.** It is never
logged, never in a document id, and never in an error message: the SDK's own failure text quotes
the token it failed to return, so only `error::class.simpleName` is ever logged. The Firestore
document is `register-device/{uid}/devices/{hash}` — the hash exists purely so the document *name*
is safe, because a `:commit` body is the one part of a write that ends up in logs and rules
traces. The collection is top-level rather than under `users`, but the uid is still a path
segment, so `isOwner(uid)` in `firestore.rules` compares it with `request.auth.uid` exactly as
it does for the rest of the database; folding the uid into the document id instead would leave
the rule only a client-chosen field to trust.
`PushRegistrarTest` asserts the token is absent from `writes[].update.name` and present in the
body. Note the path is in the body, not the URL: `:commit` takes the whole batch at once.

**`FirebaseMessagingService` in the manifest must be the app's subclass, not the SDK's.** Declaring
`com.google.firebase.messaging.FirebaseMessagingService` compiles, installs, and receives nothing —
its callbacks are empty. The class that does the work is `FcmMessagingService` in `:androidApp`,
which lives there for the same reason as `ReminderReceiver`: it needs this module's `R` for the
status-bar icon. The messaging dependency is therefore declared in *both* modules; `:composeApp`
asks for the token, `:androidApp` handles the message.

**The channel must exist before the first push, not before the first reminder.** Posting to a
channel that does not exist is not an error on API 26+ — the notification is silently dropped.
`ReminderReceiver.createChannel` only ran when a reminder fired, so a push arriving before a user's
first local reminder would vanish with nothing in the log. It is now `ReminderReceiver.ensureChannel`
and is called from `DiaryApplication.onCreate`.

**Only the `data` half of a message is handled.** A `notification`-type message is drawn by the
system whenever the app is backgrounded and handed to the service when it is not, so handling both
shows the same push twice with different chrome depending on what the app happened to be doing.
A data message is delivered to the service and nowhere else. The body is untrusted input arriving
while nobody is watching: posted verbatim, never parsed, never logged.

**Registration is fired and forgotten, deliberately.** `DefaultAuthRepository` launches it on the
injected scope and drops the result. Awaiting it would put a network round trip and a messaging SDK
in front of the login screen, and a user whose project has no messaging would watch a spinner for a
feature they never asked about. It is also called from `restoreSession`, which is the only chance a
returning user gets — they will not type a password again for days.

**Nothing in this app can send a push.** There is no Cloud Function and `firebase.json` has no
functions target, so a registered device is just a document. A sender has to be written separately,
and until Cloud Messaging is enabled on the project, `getToken` never completes.

**Bars and the trend ribbon are drawn in isometric 3D on `Canvas`. The pie is not, and must
not be.** `ui/dashboard/Chart3d.kt` is the 3D renderer: a fixed projection, a single diffuse
shading term in `shade`, and a contact shadow under every solid. Three rules hold for it, and
they are what stop it reading as clip art.

- **The top face goes on last.** Drawing it at the *bottom* of its own extrusion renders the
  shape inside-out. The companion rule -- an inner wall only for the far half -- went with the
  ring when the pie went flat, so it no longer has a shape to apply to.
- **Faces are painted in an explicit order per shape, never depth-sorted.** Every shape here is
  convex, so far wall, near wall, cap is correct in three lines and a sort would only be a
  second way to be wrong. The first version of the file carried a comment claiming it sorted by
  depth; it did not, and a comment that lies is worse than none.
- **The extrusion on a flat shape is purely vertical.** Offsetting it sideways as well displaces
  the shape from its own centre.
- **A pie is normalised by its total, never by its largest slice.** Dividing by the largest
  sounds like a reasonable way to give the biggest slice the full circle, and with two
  categories it is. A third slice then starts where the first ended, wraps past 12 o'clock and
  paints back over it: a 10/3/1 week produced 360 + 108 + 36. Nothing crashes, the image still
  looks like a chart, and the legend beside it prints the true 71/21/7 the whole time.
  `shareOfCircle` is the single place that arithmetic lives.

**The pie is flat, and that is a correctness decision rather than a style one.** It was a 3D
ring: an oblique projection, so the slices nearest the viewer measure wider than the slices at
the back, and the chart argued with its own legend about every proportion it drew. A pie whose
job is proportion cannot be seen at an angle. `raisedRingSegment` and its two rasterised tests
went with it; `Chart3dTest` now asserts the slice reaches the centre instead.

**The pie is the app's only interactive chart, so its arithmetic is in tested functions.**
`visibleSweeps` gives the entrance one shared angle budget rather than scaling each slice's own
sweep -- scaling them independently opens a gap between the last slice and the one before it
while the pie is still filling. `sliceAt` hit-tests in the same 0-at-twelve-o'clock clockwise
measure the arcs are drawn in, so drawing and hit-testing agree by construction rather than by
two conversions happening to agree. It has deliberately **no dead zone at the slice
boundaries**: the 2-degree hairline is subtracted when drawing and not in the extents, because a
gap that narrow is an unhittable target and a tap near a boundary would otherwise do nothing at
all. `MoodPieGeometryTest` pins all of it, including that every one of the 360 integer degrees
selects something.

**The centre of a pie needs a radius to reject it, because the angle cannot.** Tapping the
middle to clear the selection is the obvious gesture and it silently selected a mood instead:
`atan2(0, 0)` is 180 degrees, so a dead-centre tap resolved to a real slice -- not the one at
twelve, the one at six -- and the caption then confidently named a mood the user had not
touched. A comment in the handler claimed a centre dead zone that no code implemented, which is
the more expensive half of this bug: the intent was documented, so the next reader believed it
was already handled. The 20dp `CENTRE_DEAD_ZONE` is the fix, and the test pins the 180 degrees
rather than trying to assert the absence of a selection, because the selection state is not
reachable from the geometry functions.


**A selected slice is moved out along its own mid-angle, never scaled.** Scaling would grow the
slice and make its area lie about its share, which is the one thing this chart is for. The chosen
slice is also drawn last so it sits above its neighbours rather than being overlapped by the
slice that follows it round the circle.

**A second implementation of a calculation is a second bug.** `Chart3dGallery` used to compute
its own `weight * 360f` while the composable divided by the largest slice. The gallery's copy
happened to be the correct one, so every PNG looked right and `Chart3dTest` passed, and the
shipped `MoodDonutChart` was overlapping itself throughout. A rendered preview of a *replica* is
worth nothing as evidence about the real thing -- draw the real code, or share the function, and
assert on the function. The composable and the gallery now both call `shareOfCircle`, and
`ChartShareTest` pins the total to 360 for a spread of distributions, including the old formula
reproducing its own 504 degrees so the reason cannot be quietly undone.

`shade` has a floor of `0.55` on purpose. The mood accents are already a step off full chroma, so
a darker unlit face would push the smallest ones below the contrast they need against the card.
This is a chart, not a game: no specular lobe, because a highlight on a bar chart reads as glass
and a paper journal is not made of glass.

**A source-scanning guard finds its own needles unless the needles are assembled at runtime.**
`NoGradientsTest` exists because the no-gradient rule has no compiler behind it, and its first
version listed `"verticalGradient"` and friends as literals -- so the scan matched its own
detector and reported six offenders that were the six strings doing the looking. The list is now
built as `dir + "Gradi" + "ent("`, and the test excludes itself by filename as well. The detector
is only trustworthy because a real `Brush.linearGradient` in `ui/theme/Probe.kt` was verified to
fail it; a guard never proven to fail is not a guard. Its second assertion caught the same class
of problem one level up, where the ribbon's alpha had to be read off the line above the `Fill`
pass because that file has four alphas and only one is the ribbon body.

**A 3D chart is the one thing in this app that cannot be judged from a test result.** The
geometry can be correct and the chart can still read as flat. `Chart3dGallery` in `desktopTest`
rasterises the real drawing code to `composeApp/build/chart3d/*.png` for a human to look at.
It asserts only that the files are non-empty: a golden image that nobody regenerates is worse
than no golden. Use it whenever a chart changes, and hand the images to whoever asked.

## Firebase Storage over REST

Reached with Ktor, same as Firestore: the object API, no SDK. The path is
`users/{uid}/avatar` and `storage.rules` allows a caller only their own prefix.

Three emulator behaviours here are not guessable, and each one produced a wrong "verified"
result before being pinned down. All of them were checked against the emulator with two
separate accounts and a 16-case matrix.

**Rules are enforced on the `v0` endpoints only.** A `v1` multipart upload
(`upload/storage/v1/b/{bucket}/o?uploadType=multipart`) returns `200` for *every* case --
own path, another user's path, `text/plain`, a 6 MB body, all of it. It is a straight
bypass. Never treat a green `v1` result as evidence a rule holds; verify on `v0`.

**`v0` upload ignores the `Content-Type` header** and records `application/octet-stream`
whatever you send. A rule of the form `request.resource.contentType.matches('image/.*')`
therefore can never pass on the emulator, which is why `storage.rules` asserts ownership and
size only. The name is chosen by the app, so there is no type for a caller to smuggle, and
the bytes are re-encoded client-side before upload.

**`v1` DELETE is `501 Not Implemented`.** Delete on `v0` returns `204` and the object is
gone (`404` on the next read). Use `v0` for delete.

The read and delete URLs need the `/o/` segment: `/v0/b/{bucket}/o/{object}`. Dropping it
does not 404 cleanly -- the emulator parses `v0/b/` as part of the object name and answers
`No such object: v0/b/users/{uid}/avatar`, which looks like a missing file rather than a
malformed URL. `alt=media` on read returns the bytes; without it, the JSON metadata.

The emulator refuses unauthenticated access with `403`, not `401`, and returns `403` rather
than `404` for another user's object -- so a `404` on read is unambiguous in a way `403` is
not. Start it with a config copy that sets `emulators.ui.enabled` to false when port 4000
is taken, and note the rules path in that copy must be absolute: it resolves relative to the
config file's directory.

```
export JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.7+6/Contents/Home
firebase emulators:start --config /tmp/fb-no-ui.json --only auth,storage --project demo-diaryapp
```

## Known external blockers

- **The iOS Firebase plist contract is still unverified end to end.** `iosApp/` now exists and
  `FirebaseConfig.plist` is read by the loader, but no plist has been supplied, so the configured
  branch has never actually run on iOS -- only the offline one has. Everything iOS-verified so far
  is the local-only path.
- **Storage is not enabled on `diary-b1954`.** The API and the bucket both answer 404, and
  the Firebase CLI cannot flip a Service Usage API without `gcloud`, so it has to be
  enabled in the console. Nothing about the object API can be checked against the live
  project until then -- only the emulator.
- **Desktop window size cannot be asserted from here** — `osascript` lacks accessibility
  permission. Launch and confirm the process starts; do not claim a pixel size was verified.

## Firestore over REST

Firebase is reached with Ktor against the v1 REST API, not the SDK, and it works end to end.
Three things about that API are not guessable and each one produced a 400 that compiled,
installed, and ran:

- **A commit write's `name` is a resource name, not a URL.** It must be
  `projects/{p}/databases/(default)/documents/{path}` with no scheme, host, or `/v1` prefix.
  Passing `FirebaseConfig.documentsRoot` — which is a full URL, and the right thing for every
  `GET` — is refused with `Document name ... lacks ... at index 0`. The bare relative path is
  refused too, so `documentsResourceRoot` exists alongside `documentsRoot` for this one field.
- **`Value` has no JSON `null`.** A Kotlin `null` serialised by Compose's own substitution
  leaves a bare `null` in the wire body and Firestore answers `Cannot convert firestore.v1.Value
  with type unset`. `FirestoreCodec.nullValue` emits `{"nullValue": null}` and the decoder
  treats `nullValue` *and* a bare `null` as absent, so documents written by either shape read
  back. See `FirestoreCodecTest`.
- **The documents list wants the short direction, `asc`/`desc`.** `orderBy=field ASCENDING` —
  the proto enum name the `runQuery` structured query used — is refused with
  `Invalid order by clause`.

Do not reintroduce `runQuery` for reads. A `StructuredQuery.From.parent` is rejected as an
unknown field and a `from` list is rejected for having more than one entry, so a subcollection
read goes to `documents/{collectionPath}` directly with `pageSize`/`orderBy`/`pageToken`, and
the watermark is applied client-side. `FirestoreListQueryTest` pins the URL shape.

**Never send a Firebase id token as `Authorization: Bearer` to Identity Toolkit.** The header
is validated as an *OAuth 2 access token*, and a Firebase id token is not one, so
`accounts:delete` answers 401 `Expected OAuth 2 access token, login cookie or other valid
authentication credential` without ever looking at the `idToken` in the body. Firestore
accepts the same token as a bearer, so sync works fine and the mistake stays invisible until
an account is deleted. Identity Toolkit wants the token in the request body only; no call in
`FirebaseAuthClient` passes `bearer`.

Deleting an account also cannot be satisfied by a token refresh. `accounts:delete` answers
400 `CREDENTIAL_TOO_OLD_LOGIN_AGAIN` and wants a recent password sign-in, so `deleteAccount`
reads the token through `idToken()` — the same refreshing accessor `FirestoreClient` is given —
and the user is asked to sign in again rather than being left with an account they cannot
remove. See `AuthReasonTest` for the code-to-reason mapping.

Sync is manual: Dashboard pull-to-refresh or Profile → Sync now. Nothing calls it after a save,
which is deliberate — the local write is already durable and immediate.

`firestore.rules` is deployed and was verified against the live database: an unauthenticated
read is refused, the caller's own path reads 200, and another account's path reads 403. Re-verify
with the emulator after any edit:

```bash
export JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.7+6/Contents/Home
firebase emulators:start --only firestore
curl -s "http://127.0.0.1:8080/v1/projects/diary-b1954/databases/(default)/documents/users"
# 403 PERMISSION_DENIED is the correct result. Port 4000 may be taken; --only firestore
# still starts, or point --config at a copy with "ui": {"enabled": false}.
```

`CONFIGURATION_NOT_FOUND` on a sign-in probe means the provider is off, not that a password
is wrong:

```bash
curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"probe@example.invalid","password":"x","returnSecureToken":true}'
```

When a request is refused, `FirestoreClient` logs the status, the collection path, and
Firestore's reason with every quoted value masked. That redaction is load-bearing — a
`fieldViolation` quotes the value it rejected, and those values are diary entries — so keep it
if that log is ever touched. To see a rejection, `adb logcat -s System.out` and grep
`FirestoreClient`.

## Before you finish

1. Full verification command above, clean.
2. New strings in both locales; parity test green.
3. No diary content or credential in any log statement you added.
4. If you touched the database, re-check the Desktop second-launch case.
5. If you touched Firebase, confirm the `Generated Firebase config` line still appears.
