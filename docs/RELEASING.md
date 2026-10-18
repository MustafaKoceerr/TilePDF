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

## 3. Build the bundle

```sh
./gradlew bundleRelease
```

The signed bundle is `app/build/outputs/bundle/release/app-release.aab`. Upload
it in Play Console. Release builds are shrunk with R8; the mapping file for
crash reports is `app/build/outputs/mapping/release/mapping.txt`. Upload it with
the release so Play shows readable stack traces.

## Checking a shrunk build locally

To install the R8 build on a device without the upload key, sign it with the
debug key:

```sh
./gradlew assembleRelease -Ptilepdf.signReleaseWithDebug=true
adb install -r app/build/outputs/apk/release/app-release.apk
```

The flag is ignored when the upload key is configured, so it can never replace
real signing.
