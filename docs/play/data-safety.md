# Play Console: Data safety answers

Answers for the Data safety form, matching TilePDF 1.0.0. If a release adds a
permission or a library that sends data, update this file first. The build
fails on any permission outside the allowlist in
`build-logic/.../ReleasePermissions.kt`.

## Overview

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable: nothing is collected or sent |
| Do you provide a way for users to request that their data is deleted? | Not applicable: nothing is collected. All app data is deleted by uninstalling, or with *Settings → Apps → TilePDF → Clear storage* |

TilePDF has **no network access**. The release manifest removes
`android.permission.INTERNET`, and no analytics, crash reporting, ads or
accounts are included.

Text recognition uses Google ML Kit's bundled model, which runs on the device.
ML Kit is built to send usage metrics to Google, which Play's guidance would
count as data the app shares, but only over the network: without `INTERNET`
it can't send anything, so the answers above still hold. If `INTERNET` is
ever allowed, ML Kit's metrics must be declared here.

## What stays on the device

- PDFs the user opens are read through Android's file picker, or through "Open
  with" and "Share" from other apps. Files that can't be kept open later are
  copied into the app's private storage.
- Annotations are written into the PDF file itself. Until they are saved, a
  draft stays in the app's private database.
- The library list, reading positions and bookmarks live in the app's private
  database.
- Preferences: reading settings, theme and language.

Google's data safety guidance doesn't count on-device processing as collection.

## Android backup

Android Auto Backup is enabled, but only preferences are included (see
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`).
Documents, annotations and the library are not backed up. This backup is the
user's own end-to-end encrypted Android backup, and the developer never
receives it.

## Permissions in the release

| Permission | Requested by | Why |
|---|---|---|
| `ACCESS_NETWORK_STATE` | WorkManager | Part of WorkManager's scheduling; TilePDF's save work has no network constraint and never connects |
| `WAKE_LOCK` | WorkManager | Keeps the CPU awake while annotations are saved in the background |
| `RECEIVE_BOOT_COMPLETED` | WorkManager | Reschedules a pending annotation save after a reboot |
| `FOREGROUND_SERVICE` | WorkManager | Lets WorkManager run long work in the foreground if needed |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | androidx.core | Signature permission that keeps the app's own broadcast receivers private |

## Privacy policy URL

The text is in `docs/play/privacy-policy.md` (English) and
`privacy-policy-tr.md` (Turkish). Play Console needs a public URL for it, for
example through GitHub Pages; publishing it is the developer's step.
