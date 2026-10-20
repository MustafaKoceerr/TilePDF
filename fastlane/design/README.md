# Store graphics

Sources for everything under `fastlane/metadata/android/en-US/images/`. The
screenshots are the app's own, unedited, from the R8 release build on an
emulator; only the frame and caption around them are drawn here.

| Path | What |
|---|---|
| `icon/icon.svg` | The app icon. Same shapes and coordinates as `app/src/main/res/drawable/ic_launcher_*.xml`; keep them in step. |
| `samples/` | The PDFs the screenshots show, built by `make_samples.py` from public-domain works: *Alice's Adventures in Wonderland* with John Tenniel's illustrations, the opening chapters of *Pride and Prejudice*, *Frankenstein*, *The Art of War* (Giles) and *Moby-Dick*, a "scanned" Moby-Dick page with no text layer, and a fictional library's membership form. Texts come from Project Gutenberg, without its name or license, as its terms ask when the trademark isn't used. |
| `raw/` | Screenshots taken with `device.py shot <name>`. |
| `captions.json` | Headline and line under it for each screenshot, and for the feature graphic. Describe only what the app does. |
| `templates/` | HTML frames: `phone.html` (1080×1920), `tablet.html` (2560×1600), `feature.html` (1024×500). |
| `fonts/` | Plus Jakarta Sans (captions) and Literata (sample books), both under the SIL Open Font License; the license files are next to them. |

## Rebuilding

```sh
pip install pillow reportlab Hershey-Fonts   # pymupdf only for previews
python -I make_samples.py                    # downloads into .cache/ on first run
python -I render.py                          # all images; or: phone, tablet, feature, icon
```

Taking new screenshots (one emulator at a time, `DEVICE` picks it):

1. Install the release build signed with the debug key
   (`./gradlew assembleRelease -Ptilepdf.signReleaseWithDebug=true`), clear the app's data.
2. `python -I device.py push`, then `python -I device.py sh content call --method scan_volume --uri content://media --arg external_primary`
   so the picker sees the files, and `python -I device.py demo` for a clean status bar.
3. Open the samples with `python -I device.py open "<file name>"`, oldest first, so
   the library lists them in the order wanted. Answer the statistics card with
   *No thanks*.
4. Set up each screen by hand (`dump`, `tap`, `swipe`, `sign` for the form's
   signature, `fill_form.ps1` for its fields), then `shot <name>`.

Store screenshots can't be over 2:1, so a raw 1080×2424 phone capture is never
uploaded on its own; it always goes through a template.
