"""Renders the Play Store graphics from raw screenshots, templates and captions.

  python -I render.py            all of them
  python -I render.py phone      only the phone screenshots (also: tablet, feature, icon)

Templates are HTML; headless Chrome draws them at the exact size, and Pillow
drops the alpha channel, which Play doesn't accept outside the icon.
Screenshots are the app's own, unedited; only the frame around them is drawn.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
IMAGES = HERE.parent / "metadata" / "android" / "en-US" / "images"
CAPTIONS = json.loads((HERE / "captions.json").read_text(encoding="utf-8"))

# Each screenshot gets its own light and angle, so a row of them doesn't look stamped.
GLOWS = ["20% 0%", "80% 5%", "15% 10%", "85% 0%", "30% 5%", "75% 10%", "20% 5%", "80% 0%"]
ANGLES = [160, 200, 150, 210, 165, 195, 155, 205]


def fill(template: str, **values: str) -> str:
    html = (HERE / "templates" / template).read_text(encoding="utf-8")
    for key, value in values.items():
        html = html.replace("{{" + key + "}}", value)
    return html


def chrome_png(html: str, width: int, height: int, out: Path, alpha: bool = False) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(html, encoding="utf-8")
        shot = Path(tmp) / "shot.png"
        args = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                f"--window-size={width},{height}", "--allow-file-access-from-files", f"--screenshot={shot}"]
        if alpha:
            args.append("--default-background-color=00000000")
        subprocess.run([*args, page.as_uri()], check=True, capture_output=True, timeout=120)
        image = Image.open(shot)
        assert image.size == (width, height), image.size
        out.parent.mkdir(parents=True, exist_ok=True)
        (image.convert("RGBA") if alpha else image.convert("RGB")).save(out, optimize=True)
    print("wrote", out.relative_to(HERE.parent.parent))


def clear(folder: Path) -> None:
    for old in folder.glob("*.png"):
        old.unlink()


def phone() -> None:
    folder = IMAGES / "phoneScreenshots"
    clear(folder)
    for i, slide in enumerate(CAPTIONS["phone"]):
        html = fill("phone.html", fonts=(HERE / "fonts").as_uri(), shot=(HERE / "raw" / f"{slide['shot']}.png").as_uri(),
                    headline=slide["headline"], subline=slide["subline"], glow=GLOWS[i % 8], angle=str(ANGLES[i % 8]))
        chrome_png(html, 1080, 1920, folder / f"{i + 1}.png")


def tablet() -> None:
    for name in ("tenInchScreenshots", "sevenInchScreenshots"):
        folder = IMAGES / name
        clear(folder)
        for i, slide in enumerate(CAPTIONS["tablet"]):
            html = fill("tablet.html", fonts=(HERE / "fonts").as_uri(), shot=(HERE / "raw" / f"{slide['shot']}.png").as_uri(),
                        headline=slide["headline"], subline=slide["subline"], glow=GLOWS[i % 8], angle=str(ANGLES[i % 8]))
            chrome_png(html, 2560, 1600, folder / f"{i + 1}.png")


def feature() -> None:
    html = fill("feature.html", fonts=(HERE / "fonts").as_uri(), icon=(HERE / "icon" / "icon.svg").as_uri(),
                shot=(HERE / "raw" / f"{CAPTIONS['feature']['shot']}.png").as_uri(),
                headline=CAPTIONS["feature"]["headline"], subline=CAPTIONS["feature"]["subline"])
    chrome_png(html, 1024, 500, IMAGES / "featureGraphic.png")


def icon() -> None:
    # The full square; Play masks the corners itself, as launchers do.
    html = f'<html><body style="margin:0"><img src="{(HERE / "icon" / "icon.svg").as_uri()}" width="512" height="512" style="display:block"></body></html>'
    chrome_png(html, 512, 512, IMAGES / "icon.png", alpha=True)


if __name__ == "__main__":
    jobs = {"phone": phone, "tablet": tablet, "feature": feature, "icon": icon}
    for name in sys.argv[1:] or jobs:
        jobs[name]()
