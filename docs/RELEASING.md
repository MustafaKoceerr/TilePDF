# Releasing TilePDF

TilePDF is published on Google Play with **Play App Signing**: Google keeps the
key that signs what users install, and you sign uploads with your own *upload
key*. Nothing secret is ever stored in this repository.

## 1. Create the upload key (once)

Keep the keystore outside the repository, e.g. in your home directory, and keep
a backup of it and its passwords in a password manager.

```sh
keytool -genkeypair -v \
  -keystore ~/keys/tilepdf-upload.jks \
  -alias tilepdf-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

If the upload key is ever lost or leaked, Play Console can reset it; the app
signing key itself stays with Google.

## 2. Set the environment

The build reads exactly these four variables:

| Variable | Value |
|---|---|
| `TILEPDF_UPLOAD_KEYSTORE` | Path to the `.jks` file |
| `TILEPDF_UPLOAD_KEYSTORE_PASSWORD` | Keystore password |
| `TILEPDF_UPLOAD_KEY_ALIAS` | Key alias, e.g. `tilepdf-upload` |
| `TILEPDF_UPLOAD_KEY_PASSWORD` | Key password |

- All four set: the release build is signed with the upload key.
- None set: the release build is unsigned, and Gradle prints one warning. This
  is fine for local checks.
- Only some set: the build stops and names the missing variables (never the
  values), so a half-configured CI can't ship an unsigned build by accident.

In CI, store them as secrets and write the keystore from a base64 secret to a
temporary file before the build.

## 3. Add the Firebase config

Crash reports and statistics go to the Firebase project `tilepdf-app`
(account kocerlabs@gmail.com). Its `google-services.json` is not in the
repository (`.gitignore`):

1. Firebase console → Project settings → Your apps → TilePDF
   (`com.mustafakoceerr.tilepdf`) → download `google-services.json`.
2. Put it at `app/google-services.json`.

Without the file the app still builds, but Firebase isn't set up and nothing
is sent; use that for local work. In CI, write it from a secret like the
keystore.

Once per project, in the Firebase console: Analytics → Admin → Data settings →
Data retention: **2 months**, and leave Google signals **off** (the privacy
policy says so).

## 4. Build the bundle

```sh
./gradlew bundleRelease
```

The signed bundle is `app/build/outputs/bundle/release/app-release.aab`. Upload
it in Play Console. Release builds are shrunk with R8. With the Firebase config
present, the build uploads the R8 mapping file and the native symbols
(PDFium, ML Kit, our JNI code) to Crashlytics, so its stack traces are
readable. Also upload `app/build/outputs/mapping/release/mapping.txt` with the
release in Play Console for Play's own crash reports.

## 5. Publish on Google Play

A new personal developer account must run a closed test with at least 12
testers for 14 days before it can apply for production.

1. Create the app in Play Console (free, default language English, tr-TR as a
   second listing). Texts are in `fastlane/metadata/android/`. The graphics
   (icon, feature graphic, 8 phone and 8 tablet screenshots) are only in
   `en-US/images/`; Play shows them for the Turkish listing too. Upload them
   in the order in [fastlane/design/UPLOAD.md](../fastlane/design/UPLOAD.md)
   (frames 01–04 are the minimum set). They are rebuilt from
   `fastlane/design/` (see its README).
2. App content: privacy policy URL
   (<https://mustafakoceerr.github.io/TilePDF/privacy/>), no ads, Advertising
   ID **No**, content rating questionnaire, target audience 18+ (not
   designed for children), and the Data safety answers in
   [docs/play/data-safety.md](play/data-safety.md).
3. Internal testing: upload the AAB, add yourself, install from the Play link
   and check that a test crash shows up in Crashlytics.
4. Closed testing: the same build to at least 12 testers, kept opted in for
   14 days. Watch Crashlytics and Analytics meanwhile; fix and re-upload with
   a higher `versionCode` as needed.
5. Apply for production, then roll out in stages (e.g. 20% → 50% → 100%).

## Checking a shrunk build locally

To install the R8 build on a device without the upload key, sign it with the
debug key:

```sh
./gradlew assembleRelease -Ptilepdf.signReleaseWithDebug=true
adb install -r app/build/outputs/apk/release/app-release.apk
```

The flag is ignored when the upload key is configured, so it can never replace
real signing.
