# Development guide

This guide is the practical reference for building, running, testing, and publishing Mooary.
It complements [architecture.md](architecture.md), which explains the code design, and
[firebase-setup.md](firebase-setup.md), which explains optional notification registration.

## Requirements

- macOS, JDK 17+, Android SDK, and Xcode 26.6+.
- XcodeGen (`brew install xcodegen`) for regenerating the iOS project after `iosApp/project.yml`
  changes.
- Firebase CLI only when working with emulators or deploying rules.

The shared UI lives in `composeApp`. Android is hosted by `androidApp`; iOS is hosted by the
Xcode application in `iosApp`. Do not expect Gradle alone to create an installable iOS app:
Xcode owns signing, the simulator/device target, assets, the launch screen, and the Swift host.

## Run Android

Build the debug APK:

```bash
./gradlew :androidApp:assembleDebug
```

For this project, use `emulator-5554` for on-device checks:

```bash
adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5554 shell am force-stop com.arjun.mooary.debug
adb -s emulator-5554 shell monkey -p com.arjun.mooary.debug 1
```

The debug application id is `com.arjun.mooary.debug`; the release id is
`com.arjun.mooary`. A package rename must be treated as a cross-platform change—Android,
iOS, Firebase registration, and all configuration files need the same identifier.

## Run iOS

### Simulator

Generate the Xcode project whenever `iosApp/project.yml` changes:

```bash
cd iosApp
xcodegen generate
open iosApp.xcodeproj
```

In Xcode, select the `iosApp` scheme, choose a simulator such as **iPhone 17 Pro**, then press
Run. The Xcode build phase compiles and embeds the Kotlin framework automatically.

The equivalent command-line flow is:

```bash
xcrun simctl list devices booted
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UDID>' build
xcrun simctl install <SIMULATOR_UDID> \
  ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator/Mooary.app
xcrun simctl launch <SIMULATOR_UDID> com.arjun.mooary
```

Android Studio can edit and compile shared Kotlin code, but the final iOS application should be
launched from Xcode. If Android Studio does not show the `iosApp` run configuration, it does not
prevent iOS development; open `iosApp/iosApp.xcodeproj` in Xcode instead.

### Physical iPhone

Set a development team in the ignored `iosApp/Local.xcconfig` file:

```text
DEVELOPMENT_TEAM = YOUR_TEAM_ID
```

Then select the connected iPhone in Xcode and run. The phone must be unlocked, trusted, and have
Developer Mode enabled. Do not commit `Local.xcconfig`.

## iOS visual behaviour

The Swift host deliberately calls `.ignoresSafeArea()` so Compose receives iOS insets exactly
once. Removing it causes doubled top and bottom safe-area padding.

The iOS launch screen uses the `LaunchBackground` asset. It matches Mooary's paper colour in
light mode and espresso colour in dark mode, preventing UIKit's default white launch frame from
flashing before Compose renders. Keep this asset aligned with the page colours in
`ui/theme/Color.kt` and `iosApp/iosApp/iosApp.swift`.

Bottom tabs preserve their ViewModels for the signed-in session. This matters on iOS, where
recreating a tab briefly displays its loading state and can look like a screen flicker. Editor
and settings-detail routes remain short-lived by design, so stale draft state is never reused.

## Test and verify

Use the fast loop while editing shared code:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Before considering a shared change complete, run:

```bash
./gradlew :composeApp:desktopTest :composeApp:testAndroidHostTest \
  :composeApp:iosSimulatorArm64Test :androidApp:assembleDebug \
  :androidApp:assembleRelease :composeApp:linkDebugFrameworkIosSimulatorArm64 \
  --rerun-tasks --console=plain
```

Build success verifies compilation, not visual behaviour. For Android, install to
`emulator-5554` and inspect the accessibility tree. For iOS, build and launch from Xcode on a
simulator or device, then test every bottom tab, theme change, calendar range, reminder switch,
and a create/edit/delete diary flow.

## Optional Firebase and notifications

The application is offline-first. A missing Firebase configuration must never stop local diary,
mood, calendar, export, or reminder use.

- Keep `google-services.json`, `GoogleService-Info.plist`, `FirebaseConfig.plist`,
  `Local.xcconfig`, keystores, and service-account keys out of Git.
- The Firebase mobile API key is an app identifier, not a secret. Its safety depends on strict
  Firestore rules, API restrictions, Android package/SHA restrictions, and iOS bundle-id
  restrictions—not on hiding the file.
- Device registration documents contain a device identity, operating system, token hash, and
  last-update time. Never log raw FCM tokens or diary content.
- Build Android messaging only when intended:

```bash
./gradlew :androidApp:assembleDebug -Pdiary.firebaseMessaging=true
```

The default build keeps FCM disabled. iOS push delivery additionally needs the Push Notifications
capability and an APNs configuration; do not claim push is working until both are configured and
tested on a physical device.

See [firebase-setup.md](firebase-setup.md) before enabling APIs, creating Firebase resources, or
deploying rules.

## Public GitHub checklist

Before publishing source:

```bash
git status --short
git diff --cached --stat
git check-ignore -v google-services.json iosApp/iosApp/GoogleService-Info.plist
```

Confirm generated build folders, simulator products, Firebase configuration, signing files, and
local properties are ignored. Stage source deliberately instead of using a blind `git add .`.
Review the staged diff for tokens, diary exports, user data, and machine-specific paths.

## Troubleshooting

| Symptom | Cause and resolution |
| --- | --- |
| Android launches then fails on a resource lookup | Verify `StageComposeResources` still runs and the two Compose resource package settings match. |
| iOS has oversized top/bottom padding | Keep `.ignoresSafeArea()` on the `ComposeView` in `iosApp.swift`; Compose applies the insets. |
| iOS shows a white flash before the first screen | Regenerate the Xcode project and ensure `LaunchBackground.colorset` is present in the asset catalogue. |
| iOS does not run from Android Studio | Open `iosApp/iosApp.xcodeproj` in Xcode and run its `iosApp` scheme. |
| A tab flashes a loading screen when returning to it | Ensure the tab uses `rememberSessionViewModel`, not the per-visit `rememberViewModel`. |
| Device registration does not appear | Check Firestore rules for the device collection, confirm the Firebase config was generated, and verify the Android build uses the FCM flag. |
| FCM notification never arrives | Verify a real token on a physical Android device, Android notification permission, server credentials, and the target document. The iOS simulator cannot receive APNs pushes. |

## Documentation map

- [README.md](../README.md): product overview and quick start.
- [architecture.md](architecture.md): layers, local-first data flow, localisation, and design decisions.
- [firebase-setup.md](firebase-setup.md): Firebase project, device registration, configuration, and rules.
- [../iosApp/README.md](../iosApp/README.md): iOS-host-specific signing and command-line build details.
