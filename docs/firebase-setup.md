# Firebase setup

Sync in this app is optional. With no configuration at all the app starts, runs entirely on
the local SQLite database, and works exactly as it does with a network — that is the offline
first design, not a degraded mode. This guide covers turning the backend on.

The app reaches Firebase over its **REST APIs** rather than through an official SDK:

| Concern | Endpoint | Used for |
| --- | --- | --- |
| Identity Toolkit | `identitytoolkit.googleapis.com/v1` | sign up, sign in, password reset, email verification |
| Secure Token | `securetoken.googleapis.com/v1` | refreshing an expired ID token |
| Firestore | `firestore.googleapis.com/v1` | reading and writing documents |

No SDK means no `google-services.json` requirement at runtime, no Gradle dependency that only
applies to one of three platforms, and no initialisation step to forget. In exchange, the
bearer token has to be attached correctly by hand — see [Authentication](#authentication) —
and this document has to be accurate.

## Contents

1. [Create the project](#1-create-the-project)
2. [Enable the APIs](#2-enable-the-apis)
3. [Deploy the security rules](#3-deploy-the-security-rules)
4. [Configure each platform](#4-configure-each-platform)
5. [Run the emulators](#5-run-the-emulators)
6. [Authentication](#6-authentication)
7. [What the app writes](#7-what-the-app-writes)
8. [Verifying it works](#8-verifying-it-works)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Create the project

1. Open the [Firebase console](https://console.firebase.google.com) and create a project.
2. **Do not** add Google Analytics. The app has its own event allow-list
   (`core/analytics/AnalyticsTracker.kt`) and does not read GA.
3. Add a **Web** app and copy the `firebaseConfig` object it shows you. You only need three
   values from it — `apiKey`, `projectId` and `appId` — which is the minimum the REST clients
   require. The `messagingSenderId` is read too but is only used by push, which this app does
   not use.

If you also intend to build the Android app, register the Android app with the package name
`com.arjun.soul_note` (the debug build uses `com.arjun.soul_note.debug`) and download
`google-services.json` — [Android setup](#android) consumes it.

## 2. Enable the APIs

Enable all three of these in **Project settings → API keys → Google APIs** (or via
`gcloud services enable`):

- Identity Toolkit API
- Secure Token API
- Cloud Firestore API

Then in **Authentication → Sign-in method**, enable **Email/Password**. It is the only method
the app implements; the REST client has no Google, Apple or anonymous flow, so leaving it off
means sign-up returns an error that looks like a bad password.

Finally create the database: **Firestore Database → Create database** in production mode.
The security rules in the next step are what protect it, so create the rules immediately after.

## 3. Deploy the security rules

```bash
cp .firebaserc.example .firebaserc     # then set projectId to yours
firebase use --add                     # or: edit .firebaserc by hand
firebase deploy --only firestore:rules,firestore:indexes
```

`firestore.rules` grants access only under `users/{request.auth.uid}/…`. Ownership is derived
from the **path**, never from a field the client controls, so a client cannot claim a user id
it does not own. See the comment at the top of that file for why an earlier `userId` field
check was wrong.

`firestore.indexes.json` is intentionally empty. The only remote query the app issues is
`where updatedAt > watermark, orderBy updatedAt` on `diaryEntries` and `moods`, which
Firestore serves from its automatic single-field indexes. A composite index would cost write
quota and change nothing.

## 4. Configure each platform

Each target reads configuration from the mechanism it already uses, and each returns `null`
when the configuration is absent — which is what puts the app into offline-only mode. The
three keys required everywhere are the **API key**, the **project id** and the **app id**.

### Android

Put `google-services.json` in the repository root. Do not edit it and do not commit it; it is
git-ignored already. Then:

```bash
./gradlew :androidApp:assembleDebug
```

The `generateFirebaseConfig` task runs as part of the build and converts it into
`res/raw/firebase_config.json` inside the APK. Two details make that more than a copy:

- The console's file nests apps under `client` as a **JSON array** with `project_info` at the
  root, while the app reads a `client` **object** with `current_key` / `mobilesdk_app_id`.
  Dropping `google-services.json` into `res/raw` unchanged loads nothing, and it fails
  *silently* — the app just starts offline. The task exists to remove that mismatch.
- It picks the entry whose `client_info` has `android_client_info`, so a project that also has
  an iOS app registered still yields the Android keys.

To point the build at the emulators instead:

```bash
./gradlew :androidApp:assembleDebug -Pdiary.firebaseEmulator=true
```

The task degrades rather than fails, because an offline build must always work:

| Situation | Result |
| --- | --- |
| No `google-services.json` | message, no resource, app builds offline |
| Not valid JSON | warning, app builds offline |
| No Android client registered | warning naming the likely cause, app builds offline |
| Success | `res/raw/firebase_config.json` packaged into the APK |

To write the resource yourself instead, put this at `androidApp/src/main/res/raw/firebase_config.json`:

```json
{
  "current_key": "AIza…",
  "mobilesdk_app_id": "1:123456789012:android:1122334455667788",
  "project_id": "your-project",
  "project_number": "123456789012",
  "storage_bucket": "your-project.appspot.com",
  "emulator": false
}
```

Only `current_key`, `mobilesdk_app_id` and `project_id` are required; the rest are optional.
`emulator` is the one value the console cannot supply, because it describes where this build
points rather than anything about the project.

### iOS

`FirebaseConfig.plist` in the app bundle. It is **not** `GoogleService-Info.plist` and it is
not read from the bundle automatically — add it to the Xcode project's *Copy Bundle Resources*
phase. The keys are read by name:

| Key | Required | Notes |
| --- | --- | --- |
| `API_KEY` | yes | |
| `PROJECT_ID` | yes | |
| `APP_ID` | yes | the `mobilesdk_app_id` value |
| `AUTH_DOMAIN` | no | defaults to `<projectId>.firebaseapp.com` |
| `GCM_SENDER_ID` | no | |
| `STORAGE_BUCKET` | no | |
| `FIRESTORE_BASE_URL` | no | defaults to production |
| `IDENTITY_BASE_URL` | no | defaults to production |
| `SECURE_TOKEN_BASE_URL` | no | defaults to production |
| `EMULATOR` | no | **integer** `1` or `0` |

`EMULATOR` is read as a number, not a string: `EMULATOR` as the text `true` parses as a
non-`NSNumber` and is silently treated as off. Write `<integer>1</integer>`.

There is no `iosApp/` Xcode project in the repository yet, so this step only applies once one
exists.

### Desktop

Environment variables, or a properties file — never a compiled-in credential:

```bash
export DIARY_FIREBASE_API_KEY="AIza…"
export DIARY_FIREBASE_PROJECT_ID="your-project"
export DIARY_FIREBASE_APP_ID="1:123456789012:web:abcdef0123456789"

./gradlew :composeApp:run
```

The file form is `~/.diary-app/firebase.properties` — in your home directory, **not** the
working directory:

```properties
diary.firebase.api.key=AIza…
diary.firebase.project.id=your-project
diary.firebase.app.id=1:123456789012:web:abcdef0123456789
```

Keys are lower-cased with `_` replaced by `.`, which is what the loader looks up as a fallback
for each environment variable.

| Variable | Required | Default |
| --- | --- | --- |
| `DIARY_FIREBASE_API_KEY` | yes | — |
| `DIARY_FIREBASE_PROJECT_ID` | yes | — |
| `DIARY_FIREBASE_APP_ID` | yes | — |
| `DIARY_FIREBASE_SENDER_ID` | no | none |
| `DIARY_FIREBASE_STORAGE_BUCKET` | no | none |
| `DIARY_FIREBASE_EMULATOR` | no | `false` |
| `DIARY_FIRESTORE_EMULATOR_HOST` | no | `127.0.0.1:8080` |
| `DIARY_AUTH_EMULATOR_HOST` | no | `127.0.0.1:9099` |
| `DIARY_ENVIRONMENT` | no | `DEVELOPMENT`; `PRODUCTION` also marks the build non-debug |

## 5. Run the emulators

```bash
firebase emulators:start
```

The ports in `firebase.json` match the desktop defaults: auth and secure-token on `9099`,
Firestore on `8080`, the emulator UI on <http://localhost:4000>.

```bash
export DIARY_FIREBASE_API_KEY="fake-api-key"
export DIARY_FIREBASE_PROJECT_ID="demo-diary"
export DIARY_FIREBASE_APP_ID="1:000000000000:web:0000000000000000000000"
export DIARY_FIREBASE_EMULATOR=true
```

Any non-blank placeholder works against the emulators: they do not validate the key. Keep
`DIARY_FIREBASE_PROJECT_ID` consistent with the project you started the emulator for, because
the Firestore emulator serves a database *per project id* and will otherwise look empty.

`firebase emulators:exec` will **not** catch a broken rules file. It starts happily and only
fails when a request is evaluated, which is the same silent failure mode as a wrong
configuration. To check the rules, either start the emulator and make a real request, or
deploy to a scratch project.

## 6. Authentication

Only **email and password** is implemented. The client talks to Identity Toolkit directly:

| Operation | Request |
| --- | --- |
| Sign up | `POST /accounts:signUp` |
| Sign in | `POST /accounts:signInWithPassword` |
| Reset password | `POST /accounts:sendOobCode` |
| Verify email | `POST /accounts:sendOobCode` |
| Change password | re-authenticate, then `POST /accounts:update` |
| Delete account | `POST /accounts:delete` |
| Refresh | `POST https://securetoken.googleapis.com/v1/token` |

The session holds both an **ID token** and a **refresh token**. ID tokens are short-lived, so
they expire roughly an hour after sign-in.

### The bearer token is the ID token, not the user id

Firestore authorises every request with `Authorization: Bearer <ID token>`. The user id is
*not* a credential and cannot be used in that header — Identity Toolkit rejects it with a 401
that surfaces in the app as a `PERMISSION_DENIED` and looks exactly like a security-rules
fault.

`RemoteAuthDataSource.idToken()` is the single place this is enforced. It returns the current
ID token and, if the session has expired, refreshes it first:

```kotlin
override suspend fun idToken(): String? = refreshMutex.withLock {
    val latest = _current.value ?: return@withLock null
    if (!latest.isExpired(nowEpochSeconds())) return@withLock latest.idToken
    suspendRunCatchingApp { client.refresh(latest.refreshToken) }
        .onSuccess { persist(it) }
        .getOrNull()
        ?.idToken
}
```

Two things follow from that shape. The refresh happens under `refreshMutex`, so several
screens discovering an expired token at once produce one network call rather than several. And
it returns `null` instead of throwing when there is no session or the refresh fails, because
an unauthenticated request is a legitimate state — signed out, or offline — that the caller
already handles.

If you see `401` on every request after a correct setup, check that `idToken()` is what
`FirestoreClient`'s `tokenProvider` is wired to in `di/AppModule.kt`. It should read
`tokenProvider = { get<RemoteAuthDataSource>().idToken() }`.

## 7. What the app writes

```
users/{uid}                      profile
users/{uid}/diaryEntries/{id}    diary entries
users/{uid}/moods/{id}           mood check-ins
```

Paths come from `FirestorePaths` (`domain/model/Mood.kt`). Field names and shapes come from
`RemoteMappers`, and `firestore.rules` asserts them with both `hasAll` and `hasOnly`, so a
client cannot smuggle extra fields into a document other clients later read as trusted.

Deletion is a **tombstone**, not a row removal. A diary delete sets `deletedAt` and marks the
row `PENDING_DELETE`; the local row survives until the backend acknowledges, so an offline
delete still reaches the server later. Rows in that state are invisible to every read query,
which is what `sync_status != 'PENDING_DELETE'` means throughout the `.sq` files.

The seeded demo rows are written with `SyncStatus.SYNCED` on purpose, so demo data never
generates sync traffic.

## 8. Verifying it works

1. Start the app with configuration in place.
2. Sign in on the login screen. Offline mode only ever shows the local-only path, so reaching
   the sign-in screen at all is the first signal that config was found.
3. Create a diary entry. It appears immediately — that is the local write, and it proves
   nothing.
4. Check **Profile → Sync**, or run a manual sync. *Pending* should return to zero.
5. Confirm the document in the Firebase console under the path above. This is the step that
   actually proves the backend is reachable, and the only one that distinguishes "written
   locally" from "written to Firestore".
6. Sign out, sign back in, and confirm the entry is still there. This exercises the refresh
   path, which is where a wrong token finally shows up.

## 9. Troubleshooting

| Symptom | Cause |
| --- | --- |
| App starts offline with no warning | Configuration absent or unparseable. On Android, re-run `:androidApp:generateFirebaseConfig` and read its output. |
| Sign-up returns an error that reads like a bad password | Email/Password sign-in is not enabled in the console. |
| `401` / `PERMISSION_DENIED` on every request | The bearer token is not the ID token — see [Authentication](#6-authentication). |
| `404` / `NOT_FOUND` on every request | Wrong project id, or the Firestore database was never created. |
| Emulator starts but the app sees no data | The app's project id does not match the emulated one; the emulator keys data by project id. |
| Android config ignored | `google-services.json` has no entry with `android_client_info` — usually the wrong app was downloaded from the console. |
| iOS emulator flag ignored | `EMULATOR` must be the integer `1`, not the string `true`. |
| `firestore.indexes.json` deploy fails | It is intentionally `{"indexes": [], "fieldOverrides": []}`; do not add entries without a query that needs them. |
