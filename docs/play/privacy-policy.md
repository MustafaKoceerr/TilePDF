---
permalink: /privacy/
title: TilePDF Privacy Policy
---

# TilePDF Privacy Policy

*Effective 20 October 2026*

TilePDF is a PDF reader for Android. Your documents are opened, read,
annotated and saved entirely on your device.

## In short

- Your **files, their contents, file names, annotations and anything you type
  stay on your device**. They are never sent anywhere.
- TilePDF sends **crash reports**, so problems can be fixed. You can turn them
  off in *Settings → Privacy*.
- TilePDF sends **anonymous usage statistics only if you allow them**. It asks
  once, and you can change your answer in *Settings → Privacy* at any time.
- TilePDF has **no ads, no advertising ID and no accounts**, and the developer
  never sells or shares data.

## Crash reports (Firebase Crashlytics)

When TilePDF crashes, or a feature such as text recognition fails, the app
sends a crash report to Google Firebase Crashlytics. A report contains:

- the technical details of the error (the stack trace) and the app version;
- the device model, Android version, available memory and storage, and whether
  the app was in the foreground;
- a random installation ID created by Firebase, which isn't linked to your
  name, account or other apps;
- if you allowed usage statistics, the last few statistics events before the
  crash (see below), such as "the reader was opened".

Crash reports never contain your documents, their text, file names or paths.
They are kept for 90 days and used only to find and fix bugs. Crash reports are
on by default because the app can't be kept working without them; turn them off
in *Settings → Privacy*, and reports not yet sent are deleted.

## Usage statistics (Google Analytics for Firebase)

Only if you allow them, TilePDF counts which screens and features are used, for
example "a document was opened", "a highlight was added" or "the dark page
theme was chosen". Events carry categories only, such as a range of page counts
("11–50 pages"), never a document's name, content or exact size. Google
Analytics also records the device model, Android version, app version, country
(derived from the IP address, which isn't stored) and a random installation ID.

Statistics are never used for advertising: advertising features and the
advertising ID are turned off, and the data is kept for 2 months. If you don't
allow statistics, or turn them off later, nothing is collected.

## Text recognition (Google ML Kit)

Text recognition on scanned pages runs on your device with Google ML Kit's
built-in model; your pages are never sent anywhere. ML Kit itself may send
Google technical usage information, such as the device model, the app version
and how long recognition took, to improve ML Kit. It never contains your pages
or recognised text.

## Digital signatures

Checking digital signatures happens on your device, against the certificates
Android itself trusts. Nothing is looked up online.

## Who processes the data

Crash reports, statistics and ML Kit's usage information are processed by
Google LLC on the developer's behalf, under Google's terms for Firebase. Data
may be processed outside your country, including in the United States. See
<https://firebase.google.com/support/privacy>.

## What the app stores, and where

Everything below stays in the app's private storage on your device:

- **Documents you open.** TilePDF reads them through Android's file picker or
  when another app shares them. Some files are copied into private storage
  so they can be reopened later.
- **Annotations.** Highlights, drawings and notes are saved into the PDF file
  itself, as is recognised text. Until they are saved, a draft stays in the
  app's private database.
- **Library.** The list of documents you opened, reading positions, favorites
  and bookmarks.
- **Preferences.** Reading settings, theme, language and your privacy choices.

You can delete all of it by uninstalling TilePDF, or with *Settings → Apps →
TilePDF → Clear storage*. Removing a document from the library also deletes
its private copy and unsaved annotations.

## Android backup

If Android backup is on, Android may include TilePDF's **preferences** in your
own device backup. Your documents, annotations and library are not included.
This backup is managed by Android and your Google account, and the developer
cannot access it.

## Permissions

TilePDF asks for no runtime permissions. It uses the internet permission only
to send crash reports and, if allowed, statistics. The other install-time
permissions come from Android libraries that save annotations in the
background.

## Your rights

You can turn crash reports and statistics off at any time in the app. Because
the data is tied only to a random installation ID, the developer can't tell
which data is yours; uninstalling TilePDF or clearing its storage starts a new
ID. For any request or question about your data, including deletion, contact
the developer below.

## Children

TilePDF isn't directed at children and doesn't knowingly collect data from
them.

## Changes

If this policy changes, the new version will be published here with a new
effective date.

## Contact

Questions or requests about this policy: kocerlabs@gmail.com
