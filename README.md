# Diary & Mood Tracker

A private journal with mood tracking, built with Kotlin Multiplatform. One shared codebase
runs on Android, iOS and desktop; everything that genuinely differs (secure storage,
reminders, file export, Firebase config) has a per-platform binding.

Entries are written to a local SQLDelight database first, so the app works with no network
at all. There are no user accounts or cloud diary sync. Firebase is used only to register a
hashed device identity, its current FCM token when available, operating system, and timestamp
for notifications.

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
- **Profile** — theme, reminder schedule, export, and local preferences.
- **Reminders** — `AlarmManager` on Android, `UNUserNotificationCenter` on iOS. Daily or on
  chosen weekdays.
- **Export** — JSON (pretty-printed, versioned) and RFC-4180 CSV, written to the platform's
  documents directory and shared through the system sheet.
- **Privacy-first** — diary and mood records never leave the device.

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
| Notifications | Firebase Cloud Messaging + anonymous device registration |
| Tests | kotlin.test, kotlinx-coroutines-test |

Firebase is reached over its REST API rather than an official KMP SDK, so one client
implementation serves all three targets.

## Project layout

```
composeApp/          shared module — all targets
  commonMain/        domain, local data, platform contracts, UI
  androidMain/       Android bindings (driver, alarms, notification config)
  iosMain/           iOS bindings (notification centre, exporter, config)
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

For the complete local-development, device, iOS simulator, release, and troubleshooting guide,
see [docs/development.md](docs/development.md). The iOS app is an Xcode host, so run it from
`iosApp/iosApp.xcodeproj`; Android Studio builds the shared Kotlin module but is not the
authoritative iOS launcher for this project.

## Firebase configuration

The app is fully functional without Firebase configuration. Firebase does not receive diary,
mood, draft, profile, export, or photo data. It only supports anonymous device registration and
push notifications.

For a safe public-repository setup, local Firebase configuration, GitHub Actions secrets, and
release-signing guidance, read [docs/public-repository-setup.md](docs/public-repository-setup.md).

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

See [docs/architecture.md](docs/architecture.md) for the layering and decisions behind the
per-platform bindings.

## Privacy

Diary text never reaches a log sink: `AppLogger` calls take a tag and a lambda, and the
production logger is redacted. The database lives in a no-backup directory on Android and
exports are excluded from iCloud on iOS. FCM registration tokens are never logged.
