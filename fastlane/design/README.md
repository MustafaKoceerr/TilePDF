# Store graphics

Sources for everything under `fastlane/metadata/android/en-US/images/`. The
screenshots are the app's own, unedited, from the R8 release build on an
emulator; only what surrounds them is drawn here: frames, captions, badges,
and enlarged crops of the same screenshots.

| Path | What |
|---|---|
| `icon/icon.svg` | The app icon. Same shapes and coordinates as `app/src/main/res/drawable/ic_launcher_*.xml`; keep them in step. |
| `samples/` | The PDFs the screenshots show, built by `make_samples.py` from public-domain works: *Alice's Adventures in Wonderland* with John Tenniel's illustrations, the opening chapters of *Pride and Prejudice*, *Frankenstein*, *The Art of War* (Giles) and *Moby-Dick*, a "scanned" Moby-Dick page with no text layer, and a fictional library's membership form. Texts come from Project Gutenberg, without its name or license, as its terms ask when the trademark isn't used. |
| `raw/` | Screenshots taken with `device.py shot <name>`. |
| `slides.json` | Every frame: headline (`[word]` gets the highlighter mark), line under it, badges, where the device sits and how it tilts, lenses (`crop: [x, y, radius]` of the raw screenshot) and pop-outs (`crop: [x, y, w, h]`), notes and sparkles. Badges and words claim only what is true: Free, No ads, No account, Works offline, Open source (Apache-2.0, see `LICENSE`). |
| `render.py` | Draws each set as one wide panorama, so the background flows from frame to frame, then cuts it into frames named in upload order (`01_read.png` …). Lenses and pop-outs are enlarged crops of the same unedited screenshot. |
| `UPLOAD.md` | Which file goes in which Play Console slot, in which order; the minimum set is frames 01–04. |
| `fonts/` | Plus Jakarta Sans (captions), Caveat (handwritten notes) and Literata (sample books), all under the SIL Open Font License; the license files are next to them. |
| `icons/` | Material Symbols (Apache 2.0) used in badges. |

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
