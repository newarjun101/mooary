# iosApp

The iOS host application. `iosApp.xcodeproj` is **generated, not committed** — `project.yml` is
the source of truth, so a change to the project is a reviewable diff rather than a conflict in
400 lines of `pbxproj`.

## Requirements

- Xcode 26.6 or newer, iOS 15.0+ deployment target
- `brew install xcodegen`
- JDK on `JAVA_HOME` (the build phase shells out to Gradle)

## Regenerate after editing `project.yml`

```bash
cd iosApp && xcodegen generate
```

## Run in the simulator

```bash
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,id=AC14C8D5-37A9-40E1-BF5D-12357729246E' \
  -configuration Debug build

xcrun simctl install    <udid> ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator/SoulNote.app
xcrun simctl launch     <udid> com.arjun.soul_note
```

Prefer `-destination id=...` over `name=...`: several simulators share a device name across iOS
versions, and `name=` fails as ambiguous rather than picking one.

Opening `iosApp.xcodeproj` in Xcode and pressing Run also works — the *Compile Kotlin Framework*
build phase invokes Gradle first.

## Run on a physical device

1. Connect the iPhone, unlock it, and choose **Trust This Computer**. Until then
   `xcrun devicectl list devices` reports it `unavailable`.
2. Enable **Settings → Privacy & Security → Developer Mode** and restart.
3. Set your signing team:

   ```bash
   printf 'DEVELOPMENT_TEAM = <TEAMID>\n' > Local.xcconfig
   ```

   `Local.xcconfig` is git-ignored and is pulled in by `Configs/Debug.xcconfig` with
   `#include?`. It has to be an *optional* include — a missing include is an xcconfig parse
   error, not an ignored setting — so a fresh clone builds without it. Xcode's own
   *Signing & Capabilities* UI works too, but be aware that a target's build settings beat its
   base configuration: `DEVELOPMENT_TEAM` is deliberately **absent** from `project.yml` rather
   than set to `""`, because an empty setting still overrides and would silently shadow the file.

4. If you want to change the bundle identifier, change `PRODUCT_BUNDLE_IDENTIFIER` in
   `project.yml` and re-generate. It must stay unique, or provisioning will fail.
5. Build with a device destination:

```bash
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS' -configuration Debug \
  -allowProvisioningUpdates build
```

   `-allowProvisioningUpdates` lets Xcode register the App ID and create the provisioning
   profile for you, which needs your Apple account to be reachable. Without it the build
   stops at *"No profiles for 'com.arjun.soul_note' were found"* — which is a provisioning
   problem, **not** a missing-team one, so read the two errors differently:

| Error | Meaning |
| --- | --- |
| `Signing for "iosApp" requires a development team` | no team anywhere; set `Local.xcconfig` |
| `No profiles for 'com.arjun.soul_note' were found` | team found, profile not created yet — add the flag above |

Simulator builds need neither: they ad-hoc sign with `-` and no team is required.

The Gradle phase picks the right Kotlin target from the SDK: `iosArm64` for device,
`iosSimulatorArm64` for simulator, and `Debug`/`Release` from the Xcode configuration. Both
paths are listed in `Configs/*.xcconfig` because a search path that does not exist is
harmless, whereas a missing one is a linker error naming only a module.

## Firebase (optional)

The app runs **offline** without any Firebase configuration — that is the intended default, and
`loadFirebaseConfig()` returning null is what selects the local-only data sources.

To enable sync, supply `FirebaseConfig.plist` in the app bundle. It must carry `API_KEY`,
`PROJECT_ID` and `APP_ID`; the remaining keys are optional. The loader is
`composeApp/src/iosMain/.../core/platform/Platform.ios.kt`, which names the constants. The file
is generated from your own `GoogleService-Info.plist` and is git-ignored, so no key is ever
committed.

APNs and the Push Notifications capability are **not** configured. Nothing in this app sends a
push, and adding the capability without an APNs key produces a provisioning error rather than a
working feature.