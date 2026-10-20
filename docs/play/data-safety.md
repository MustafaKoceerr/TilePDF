# Play Console: Data safety answers

Answers for the Data safety form, matching TilePDF 1.0.0. If a release adds a
permission or a library that sends data, update this file first. The build
fails on any permission outside the allowlist in
`build-logic/.../ReleasePermissions.kt`.

## What sends data

| Source | When | What |
|---|---|---|
| Firebase Crashlytics | On by default; off in *Settings → Privacy* | Crash and error reports, device and app state, Firebase installation ID |
| Google Analytics for Firebase | Only after the user allows it (one-time card in the library, or *Settings → Privacy*) | Screen views and feature events with categories only, device model, OS and app version, country from IP, app instance ID |
| Google ML Kit (text recognition) | Whenever text recognition runs; can't be turned off | ML Kit's own diagnostics: device model, app version, performance |

Documents, their text, file names, paths and annotations are never sent. The
advertising ID permission (`AD_ID`), Privacy Sandbox ad services and the
install referrer are removed from the manifest, and ad personalization signals
are off.

## Overview

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (Firebase and ML Kit use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: by email to the address in the privacy policy. Uninstalling or clearing storage also starts a new installation ID |

## Data types

"Shared" is **No** for every type: Google processes the data on the
developer's behalf as a service provider, which Play doesn't count as sharing.
None of it is processed ephemerally.

| Category → type | Collected | Optional? | Purposes |
|---|---|---|---|
| App info and performance → **Crash logs** | Yes | Yes (users can turn it off) | Analytics |
| App info and performance → **Diagnostics** | Yes | No (ML Kit's diagnostics can't be turned off) | Analytics |
| App activity → **App interactions** | Yes | Yes (only with consent) | Analytics |
| Device or other IDs → **Device or other IDs** | Yes | No (Firebase installation ID used by Crashlytics and ML Kit) | Analytics |
| Location → **Approximate location** | Yes | Yes (only with consent; country/city derived from IP by Google Analytics) | Analytics |

Not collected: personal info, financial info, health, messages, photos and
videos, audio, files and docs, calendar, contacts, web browsing, precise
location, installed apps, in-app search history.

Google's data safety guidance doesn't count on-device processing as
collection, so the PDFs the app opens and annotates aren't "Files and docs":
they never leave the device.

## Advertising ID

In Play Console → App content → Advertising ID: **No**, the app doesn't use an
advertising ID. The merged manifest has no `com.google.android.gms.permission.AD_ID`.

## Android backup

Android Auto Backup is enabled, but only preferences are included (see
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`).
Documents, annotations and the library are not backed up. This backup is the
user's own end-to-end encrypted Android backup, and the developer never
receives it.

## Permissions in the release

| Permission | Requested by | Why |
|---|---|---|
| `INTERNET` | Firebase, ML Kit | Sends crash reports, consented statistics and ML Kit diagnostics |
| `ACCESS_NETWORK_STATE` | WorkManager, Firebase | Scheduling; Firebase waits for a connection before sending |
| `WAKE_LOCK` | WorkManager, Firebase | Keeps the CPU awake while annotations are saved in the background |
| `RECEIVE_BOOT_COMPLETED` | WorkManager | Reschedules a pending annotation save after a reboot |
| `FOREGROUND_SERVICE` | WorkManager | Lets WorkManager run long work in the foreground if needed |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | androidx.core | Signature permission that keeps the app's own broadcast receivers private |

## Privacy policy URL

The text is in `docs/play/privacy-policy.md` (English) and
`privacy-policy-tr.md` (Turkish), published at
<https://mustafakoceerr.github.io/TilePDF/privacy/> and
<https://mustafakoceerr.github.io/TilePDF/privacy/tr/>. The app links to it
from *Settings → Privacy*.
