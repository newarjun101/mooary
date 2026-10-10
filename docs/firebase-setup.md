# Firebase setup

Mooary is a local-first diary. Firebase is not used for accounts, diary entries, moods,
profiles, exports, photos, storage, or synchronisation.

The app uses Firebase only to create an anonymous credential and write a device-registration
record for notifications. The record contains a hashed device identifier, FCM token when one
is available, operating system, and update timestamp. The app cannot read those records.

For the public-repository checklist, GitHub Actions secrets, and signing guidance, see
[public-repository-setup.md](public-repository-setup.md).

## Firebase Console

1. Create or select a Firebase project.
2. Enable **Anonymous** sign-in in **Authentication → Sign-in method**.
3. Create a Firestore database.
4. Register the Android application id `com.arjun.mooary` and the matching iOS bundle id.
5. Download each platform's Firebase configuration file without committing it.

## Local configuration

### Android

Place `google-services.json` in the repository root, then build with messaging enabled:

```bash
./gradlew :androidApp:assembleDebug -Pdiary.firebaseMessaging=true
```

The Gradle build generates the packaged Firebase resource. The source configuration and
generated resources must remain untracked.

### iOS

Place `GoogleService-Info.plist` only in your local `iosApp/iosApp/` project and build the
host app from Xcode. The file is ignored by Git.

## Firestore rules

Deploy the repository rules after reviewing them:

```bash
cp .firebaserc.example .firebaserc
# Set your Firebase project id in .firebaserc.
firebase deploy --only firestore:rules --project YOUR_PROJECT_ID
```

The desired policy allows authenticated anonymous clients to create or update only their
device-registration document. It denies client reads, diary and mood storage, user profiles,
and notification sending.

## Verification

Launch the app once. On Android, use Logcat and look for the privacy-safe result:

```text
I/DeviceRegistration: device registration stored
```

An Android emulator may not receive an FCM token. That is expected: the app still writes the
hashed device identity, operating system, and timestamp, then adds a token on a later launch
when Firebase supplies one. Tokens and device identifiers must never be logged.
