# Public repository setup

This guide covers a safe public clone of Mooary. The app is local-first: diary entries,
moods, drafts, profile data, exports, and reminder preferences stay in the local SQLDelight
database. Firebase is used only for anonymous device registration and push notifications.

## Before publishing

Never commit any of the following:

- Firebase Admin SDK or service-account JSON files.
- Android signing keys (`.jks`, `.keystore`) or `signing.properties`.
- Apple signing files (`.p12`, `.p8`, `.mobileprovision`) or provisioning profiles.
- `google-services.json`, `GoogleService-Info.plist`, `.firebaserc`, `local.properties`, or
  `.env` files.

The repository's `.gitignore` covers these paths. Verify the staged content before every
push:

```bash
git status --short
git diff --cached --stat
git grep -n -I -- 'PRIVATE KEY' 'service_account' 'AIza'
```

`google-services.json` contains an Android API key. A mobile API key is not a server secret,
because it is shipped inside the installed app, but keeping local configuration out of the
repository makes it harder to accidentally connect forks to the production project. Restrict
that key in Google Cloud to Mooary's Android application id and release signing certificate.

## Local Firebase configuration

Firebase is optional for local development. Without configuration, the app remains fully
usable as a local diary.

### Android

1. In Firebase Console, register `com.arjun.mooary`.
2. Download the matching `google-services.json`.
3. Put it at the repository root. It is ignored by Git.
4. Build the debug APK:

```bash
./gradlew :androidApp:assembleDebug -Pdiary.firebaseMessaging=true
```

The build generates the packaged Firebase resource from that local file. Do not copy the
generated resource into source control.

### iOS

1. Register the iOS bundle id in Firebase Console.
2. Download `GoogleService-Info.plist`.
3. Add it only to your local `iosApp/iosApp/` host project; Git ignores it.
4. Generate/open the Xcode project following `iosApp/README.md`, then build from Xcode.

### Firestore rules

Deploy rules only from a trusted machine with Firebase CLI access:

```bash
cp .firebaserc.example .firebaserc
# Edit .firebaserc with your Firebase project id.
firebase deploy --only firestore:rules --project YOUR_PROJECT_ID
```

Review `firestore.rules` before deployment. The intended public-app policy is to allow only
anonymous writes to `register-device`, with no client reads, and to deny every diary, mood,
profile, storage, and notification-send path.

## GitHub Actions deployment

The workflow in `.github/workflows/ci-cd.yml` verifies Android, desktop, and iOS. Its deploy
job runs only when manually started on `main` and uses GitHub-hosted credentials.

Create a GitHub Environment named `firebase-production`, then configure:

- Secret `FIREBASE_SERVICE_ACCOUNT_B64`: Base64-encoded contents of a Firebase service-account
  JSON file.
- Variable `FIREBASE_PROJECT_ID`: the Firebase project id.

Do not put either value in the workflow YAML, Gradle files, or repository files. Limit the
service account to the smallest Firebase permissions needed for deployments, protect the
environment with required reviewers, and rotate the service account immediately if it is ever
exposed.

## Release signing

Store Android keystores and Apple signing credentials in a secure secret manager or in the
appropriate GitHub encrypted secrets. Supply them only while building a release. A debug APK
does not need release signing material.

## After publishing

Turn on secret scanning and Dependabot in the GitHub repository settings. Keep Firebase API
restrictions and Firestore rules reviewed whenever the app's backend surface changes.
