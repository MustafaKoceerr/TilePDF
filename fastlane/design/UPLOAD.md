# Uploading the store graphics

Play Console → Grow → Store presence → Main store listing (English, default).
Upload the files below **in this order**; Play shows them in upload order, and
the first two or three are the ones most people see. The Turkish listing
uses the same graphics, so nothing is uploaded there.

All files are in `fastlane/metadata/android/en-US/images/`.

| Slot | File | Size |
|---|---|---|
| App icon | `icon.png` | 512 × 512, 32-bit PNG |
| Feature graphic | `featureGraphic.png` | 1024 × 500 |

## Minimum set (enough to publish and to be eligible for featuring)

Play needs at least 4 phone screenshots, and 4 per tablet size, to feature an app.

| Order | Phone screenshots | 7-inch tablet | 10-inch tablet |
|---|---|---|---|
| 1 | `phoneScreenshots/01_read.png` | `sevenInchScreenshots/01_contents.png` | `tenInchScreenshots/01_contents.png` |
| 2 | `phoneScreenshots/02_markup.png` | `sevenInchScreenshots/02_spread.png` | `tenInchScreenshots/02_spread.png` |
| 3 | `phoneScreenshots/03_forms.png` | `sevenInchScreenshots/03_night.png` | `tenInchScreenshots/03_night.png` |
| 4 | `phoneScreenshots/04_ocr.png` | `sevenInchScreenshots/04_pages.png` | `tenInchScreenshots/04_pages.png` |

## Maximum set (Play allows 8 per device type)

The minimum set, then these, continuing the same order:

| Order | Phone screenshots | 7-inch tablet | 10-inch tablet |
|---|---|---|---|
| 5 | `phoneScreenshots/05_search.png` | `sevenInchScreenshots/05_form.png` | `tenInchScreenshots/05_form.png` |
| 6 | `phoneScreenshots/06_themes.png` | `sevenInchScreenshots/06_search.png` | `tenInchScreenshots/06_search.png` |
| 7 | `phoneScreenshots/07_pages.png` | `sevenInchScreenshots/07_ocr.png` | `tenInchScreenshots/07_ocr.png` |
| 8 | `phoneScreenshots/08_library.png` | `sevenInchScreenshots/08_library.png` | `tenInchScreenshots/08_library.png` |

The 7-inch and 10-inch files are the same images (2560 × 1600); Play accepts
them in both slots.

With fastlane, `fastlane supply` uploads each folder in file-name order, which
is the order above.
