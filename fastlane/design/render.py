"""Renders the Play Store graphics from raw screenshots and slides.json.

  python -I render.py            everything
  python -I render.py phone      only the phone set (also: tablet, feature, icon)

Each set is drawn as one wide panorama, so the background flows from one
screenshot to the next, then cut into frames. The screen inside each device
is the app's own, unedited; lenses and pop-outs are enlarged crops of that
same screenshot. Decoration (marks, sparkles, notes) stays outside the screen.
Headless Chrome draws the HTML; Pillow drops the alpha channel, which Play
doesn't accept outside the icon.
"""
import html
import json
import math
import random
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
IMAGES = HERE.parent / "metadata" / "android" / "en-US" / "images"
SLIDES = json.loads((HERE / "slides.json").read_text(encoding="utf-8"))
FONTS = (HERE / "fonts").as_uri()
ICONS = HERE / "icons"
# Both tablet folders get the same frames; the suffix tells the copies apart.
SUFFIX = {"sevenInchScreenshots": "-7in", "tenInchScreenshots": "-10in"}

# The raw screenshot size per device kind, and the frame drawn around it.
KINDS = {
    "phone": {"canvas": (1080, 1920), "raw": (1080, 2424), "bezel": 16, "radius": 64},
    "tablet": {"canvas": (2560, 1600), "raw": (2560, 1600), "bezel": 26, "radius": 56},
}


def icon(name: str, size: int, color: str = "#fff") -> str:
    svg = (ICONS / f"{name}.svg").read_text(encoding="utf-8")
    return svg.replace("<svg ", f'<svg fill="{color}" style="width:{size}px;height:{size}px;flex:none" ', 1)


def headline(text: str) -> str:
    """[word] becomes the marked word: a highlighter stroke behind it."""
    out = html.escape(text)
    return out.replace("[", '<span class="mark">').replace("]", "</span>")


class Device:
    """Where a raw screenshot sits in a frame: screen width, top centre and a tilt."""

    def __init__(self, kind: dict, spec: dict):
        self.raw_w, self.raw_h = kind["raw"]
        self.w = spec["w"]
        self.scale = self.w / self.raw_w
        self.h = self.raw_h * self.scale
        self.cx = spec["x"]
        self.cy = spec["y"] + self.h / 2
        self.rot = math.radians(spec.get("rot", 0))

    def point(self, rx: float, ry: float) -> tuple[float, float]:
        """A raw screenshot pixel, in frame coordinates."""
        x = rx * self.scale - self.w / 2
        y = ry * self.scale - self.h / 2
        c, s = math.cos(self.rot), math.sin(self.rot)
        return self.cx + x * c - y * s, self.cy + x * s + y * c


def crop_style(shot: str, raw_w: int, raw_h: int, x: float, y: float, w: float, h: float, out_w: float) -> str:
    k = out_w / w
    return (f"background-image:url('{shot}');background-size:{raw_w * k:.1f}px {raw_h * k:.1f}px;"
            f"background-position:{-x * k:.1f}px {-y * k:.1f}px;width:{out_w:.1f}px;height:{h * k:.1f}px;")


def slide_html(kind_name: str, slide: dict, index: int) -> str:
    kind = KINDS[kind_name]
    cw, ch = kind["canvas"]
    shot = (HERE / "raw" / f"{slide['shot']}.png").as_uri()
    dev = Device(kind, slide["device"])
    left = index * cw
    parts = []
    layout = slide.get("layout", "top")

    # Words: headline, line under it, badges.
    badges = "".join(f'<div class="badge">{icon(i, 34 if kind_name == "phone" else 44)}<span>{html.escape(t)}</span></div>'
                     for i, t in slide.get("badges", []))
    text_cls = "text left" if layout == "left" else "text"
    parts.append(f'<div class="{text_cls}"><h1>{headline(slide["headline"])}</h1>'
                 f'<p>{html.escape(slide["subline"])}</p>'
                 f'{f"<div class=badges>{badges}</div>" if badges else ""}</div>')

    # Connector lines and the rings they start from, under everything else.
    lines = []
    for lens in slide.get("lenses", []):
        tx, ty = dev.point(lens["crop"][0], lens["crop"][1])
        ring = lens["crop"][2] * dev.scale
        lx, ly = lens["at"]
        # Amber, so the ring shows on white pages as well as on the background.
        lines.append(f'<line x1="{tx:.0f}" y1="{ty:.0f}" x2="{lx:.0f}" y2="{ly:.0f}" stroke="#FFB84D" stroke-width="6" '
                     f'stroke-dasharray="1 14" stroke-linecap="round"/>')
        lines.append(f'<circle cx="{tx:.0f}" cy="{ty:.0f}" r="{ring:.0f}" fill="rgba(255,184,77,.10)" stroke="#FFB84D" stroke-width="6"/>')

    # The device.
    b, r = kind["bezel"], kind["radius"]
    parts.append(
        f'<div class="device" style="left:{dev.cx - dev.w / 2 - b:.0f}px;top:{dev.cy - dev.h / 2 - b:.0f}px;'
        f'width:{dev.w:.0f}px;height:{dev.h:.0f}px;padding:{b}px;border-radius:{r + b}px;'
        f'transform:rotate({slide["device"].get("rot", 0)}deg)">'
        f'<img src="{shot}" style="width:{dev.w:.0f}px;height:{dev.h:.0f}px;border-radius:{r}px"></div>')
    parts.append(f'<svg class="overlay" width="{cw}" height="{ch}">{"".join(lines)}</svg>')

    # Pop-outs: enlarged crops of the same screenshot, lifted off the screen.
    for pop in slide.get("pops", []):
        x, y, w, h = pop["crop"]
        px, py = pop["at"]
        parts.append(f'<div class="pop" style="left:{px}px;top:{py}px;transform:rotate({pop.get("rot", 0)}deg);'
                     f'{crop_style(shot, dev.raw_w, dev.raw_h, x, y, w, h, pop["w"])}"></div>')

    # Lenses: a round magnified crop.
    for lens in slide.get("lenses", []):
        cx, cy, rr = lens["crop"]
        d = lens["size"]
        lx, ly = lens["at"]
        k = d / (2 * rr)
        parts.append(f'<div class="lens" style="left:{lx - d / 2:.0f}px;top:{ly - d / 2:.0f}px;width:{d}px;height:{d}px;'
                     f"background-image:url('{shot}');background-size:{dev.raw_w * k:.1f}px {dev.raw_h * k:.1f}px;"
                     f'background-position:{-(cx - rr) * k:.1f}px {-(cy - rr) * k:.1f}px;"></div>')

    # Handwritten notes with an arrow.
    for note in slide.get("notes", []):
        nx, ny = note["at"]
        parts.append(f'<div class="note" style="left:{nx}px;top:{ny}px;transform:rotate({note.get("rot", -4)}deg)">'
                     f'{html.escape(note["text"])}</div>')
        if "arrow" in note:
            x1, y1, x2, y2, bend = note["arrow"]
            mx, my = (x1 + x2) / 2 + bend, (y1 + y2) / 2 - bend
            ang = math.atan2(y2 - my, x2 - mx)
            head = [(x2 - 26 * math.cos(ang - 0.5), y2 - 26 * math.sin(ang - 0.5)),
                    (x2 - 26 * math.cos(ang + 0.5), y2 - 26 * math.sin(ang + 0.5))]
            parts.append(f'<svg class="overlay" width="{cw}" height="{ch}"><path d="M{x1},{y1} Q{mx:.0f},{my:.0f} {x2},{y2}" '
                         f'fill="none" stroke="#FFD166" stroke-width="6" stroke-linecap="round"/>'
                         f'<path d="M{head[0][0]:.0f},{head[0][1]:.0f} L{x2},{y2} L{head[1][0]:.0f},{head[1][1]:.0f}" '
                         f'fill="none" stroke="#FFD166" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/></svg>')

    for sx, sy, size in slide.get("sparkles", []):
        parts.append(f'<div class="sparkle" style="left:{sx}px;top:{sy}px">{icon("auto_awesome", size, "#FFE7A3")}</div>')
    for name, sx, sy, size, rot in slide.get("icons", []):
        parts.append(f'<div class="sparkle" style="left:{sx}px;top:{sy}px;transform:rotate({rot}deg)">{icon(name, size, "rgba(255,255,255,.9)")}</div>')

    return f'<section style="left:{left}px;width:{cw}px;height:{ch}px">{"".join(parts)}</section>'


def background(width: int, height: int, seed: int) -> str:
    """Soft tiles and a ribbon that run across every frame of the panorama."""
    rnd = random.Random(seed)
    shapes = []
    step = height * 0.55
    x = -step / 2
    while x < width + step:
        size = rnd.uniform(0.28, 0.55) * height
        y = rnd.uniform(-0.15, 0.85) * height
        shapes.append(f'<rect x="{x:.0f}" y="{y:.0f}" width="{size:.0f}" height="{size:.0f}" rx="{size * 0.18:.0f}" '
                      f'transform="rotate({rnd.uniform(-18, 18):.1f} {x + size / 2:.0f} {y + size / 2:.0f})" '
                      f'fill="#fff" opacity="{rnd.uniform(0.04, 0.09):.3f}"/>')
        x += step * rnd.uniform(0.8, 1.3)
    ribbon = " ".join(f"{'M' if i == 0 else 'L'}{i * 40},{height * (0.62 + 0.16 * math.sin(i * 40 / height * 2.1)):.0f}"
                      for i in range(width // 40 + 2))
    glow = "".join(f'<circle cx="{i * height * 1.1 + height * 0.3:.0f}" cy="{height * (0.15 + 0.6 * (i % 2)):.0f}" '
                   f'r="{height * 0.45:.0f}" fill="url(#glow)"/>' for i in range(int(width / height / 1.1) + 2))
    return (f'<svg class="bg" width="{width}" height="{height}"><defs>'
            f'<linearGradient id="g" x1="0" y1="0" x2="{width}" y2="{height}" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="#8B6BE8"/><stop offset=".35" stop-color="#6650A4"/>'
            f'<stop offset=".65" stop-color="#4A3B9C"/><stop offset="1" stop-color="#7A5AD8"/></linearGradient>'
            f'<radialGradient id="glow"><stop offset="0" stop-color="#fff" stop-opacity=".16"/>'
            f'<stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>'
            f'<rect width="{width}" height="{height}" fill="url(#g)"/>{glow}{"".join(shapes)}'
            f'<path d="{ribbon}" fill="none" stroke="#fff" stroke-opacity=".10" stroke-width="{height * 0.04:.0f}"/></svg>')


def css(kind_name: str) -> str:
    phone = kind_name == "phone"
    return f"""
  @font-face {{ font-family: Jakarta; src: url({FONTS}/PlusJakartaSans.ttf); font-weight: 200 800; }}
  @font-face {{ font-family: Caveat; src: url({FONTS}/Caveat.ttf); font-weight: 400 700; }}
  html, body {{ margin: 0; overflow: hidden; }}
  body {{ font-family: Jakarta, sans-serif; color: #fff; position: relative; }}
  .bg {{ position: absolute; left: 0; top: 0; }}
  section {{ position: absolute; top: 0; overflow: hidden; }}
  .text {{ position: absolute; top: {100 if phone else 190}px; left: 70px; right: 70px; text-align: center; z-index: 5; }}
  .text.left {{ left: 140px; right: auto; width: 780px; text-align: left; }}
  h1 {{ margin: 0; font-weight: 800; font-size: {78 if phone else 92}px; line-height: 1.08; letter-spacing: -0.5px;
        text-shadow: 0 4px 24px rgba(30,16,80,.25); }}
  .mark {{ background: linear-gradient(transparent 58%, rgba(255,184,77,.95) 58%, rgba(255,184,77,.95) 92%, transparent 92%);
           padding: 0 .08em; border-radius: .08em; }}
  p {{ margin: 22px auto 0; font-weight: 500; font-size: {35 if phone else 46}px; line-height: 1.32; color: rgba(255,255,255,.9); }}
  .text.left p {{ margin-left: 0; }}
  .badges {{ display: flex; gap: {14 if phone else 20}px; justify-content: {"center" if phone else "flex-start"}; flex-wrap: wrap; margin-top: {30 if phone else 44}px; }}
  .text.left .badges {{ justify-content: flex-start; }}
  .badge {{ display: flex; align-items: center; gap: 10px; padding: {12 if phone else 16}px {22 if phone else 30}px {12 if phone else 16}px {16 if phone else 22}px;
            border-radius: 999px; background: rgba(255,255,255,.16); border: 2px solid rgba(255,255,255,.38);
            backdrop-filter: blur(10px); font-weight: 700; font-size: {29 if phone else 34}px; box-shadow: 0 10px 30px rgba(20,10,60,.18); }}
  .device {{ position: absolute; background: linear-gradient(145deg, #2c2834, #0c0a11); z-index: 2;
             box-shadow: 0 60px 110px rgba(16,8,48,.55), inset 0 0 0 3px #3d3847; }}
  .device img {{ display: block; }}
  .overlay {{ position: absolute; left: 0; top: 0; z-index: 3; pointer-events: none; }}
  .pop {{ position: absolute; z-index: 4; border-radius: {22 if phone else 28}px; border: {6 if phone else 8}px solid #fff;
          box-shadow: 0 30px 70px rgba(16,8,48,.5); background-repeat: no-repeat; }}
  .lens {{ position: absolute; z-index: 4; border-radius: 50%; border: {10 if phone else 14}px solid #FFB84D; background-repeat: no-repeat;
           box-shadow: 0 30px 80px rgba(16,8,48,.55), inset 0 0 0 3px rgba(0,0,0,.06); }}
  .note {{ position: absolute; z-index: 6; font-family: Caveat, cursive; font-weight: 700; font-size: {58 if phone else 74}px;
           color: #FFD166; white-space: nowrap; text-shadow: 0 3px 14px rgba(20,10,60,.35); }}
  .sparkle {{ position: absolute; z-index: 6; filter: drop-shadow(0 4px 10px rgba(20,10,60,.3)); }}
"""


def chrome_png(html_text: str, width: int, height: int, out: Path) -> Image.Image:
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(html_text, encoding="utf-8")
        shot = Path(tmp) / "shot.png"
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        f"--window-size={width},{height}", "--allow-file-access-from-files", f"--screenshot={shot}", page.as_uri()],
                       check=True, capture_output=True, timeout=300)
        image = Image.open(shot).convert("RGB")
        image.load()
    assert image.size == (width, height), image.size
    return image


def render_set(kind_name: str, folders: list[str]) -> None:
    slides = SLIDES[kind_name]
    cw, ch = KINDS[kind_name]["canvas"]
    width = cw * len(slides)
    body = "".join(slide_html(kind_name, s, i) for i, s in enumerate(slides))
    page = (f"<!doctype html><html lang=en><meta charset=utf-8><style>{css(kind_name)}"
            f"html,body{{width:{width}px;height:{ch}px}}</style><body>{background(width, ch, 7 if kind_name == 'phone' else 11)}{body}</body></html>")
    panorama = chrome_png(page, width, ch, HERE / ".cache" / f"{kind_name}-panorama.png")
    panorama.save(HERE / ".cache" / f"{kind_name}-panorama.png")
    for folder in folders:
        target = IMAGES / folder
        target.mkdir(parents=True, exist_ok=True)
        for old in target.glob("*.png"):
            old.unlink()
        for i, s in enumerate(slides):
            out = target / f"{s['file']}{SUFFIX.get(folder, '')}.png"
            panorama.crop((i * cw, 0, (i + 1) * cw, ch)).save(out, optimize=True)
            print("wrote", out.relative_to(HERE.parent.parent))


def feature() -> None:
    f = SLIDES["feature"]
    kind = {"canvas": (1024, 500), "raw": (1080, 2424), "bezel": 9, "radius": 34}
    KINDS["feature"] = kind
    shot = (HERE / "raw" / f"{f['shot']}.png").as_uri()
    dev = Device(kind, f["device"])
    badges = "".join(f'<div class="badge">{icon(i, 22)}<span>{html.escape(t)}</span></div>' for i, t in f["badges"])
    pops = "".join(f'<div class="pop" style="left:{p["at"][0]}px;top:{p["at"][1]}px;transform:rotate({p.get("rot", 0)}deg);'
                   f'{crop_style(shot, 1080, 2424, *p["crop"], p["w"])}"></div>' for p in f.get("pops", []))
    sparkles = "".join(f'<div class="sparkle" style="left:{x}px;top:{y}px">{icon("auto_awesome", s, "#FFE7A3")}</div>'
                       for x, y, s in f.get("sparkles", []))
    body = f"""
      <div style="position:absolute;left:64px;top:92px;width:560px;z-index:5">
        <div style="display:flex;align-items:center;gap:22px">
          <img src="{(HERE / 'icon' / 'icon.svg').as_uri()}" style="width:96px;height:96px;border-radius:24px;box-shadow:0 14px 30px rgba(18,10,48,.35)">
          <div style="font-weight:800;font-size:70px;letter-spacing:-.5px">TilePDF</div></div>
        <div style="margin-top:26px;font-weight:700;font-size:36px;line-height:1.2">{headline(f['headline'])}</div>
        <div class="badges" style="justify-content:flex-start;margin-top:22px;gap:10px">{badges}</div>
      </div>
      <div class="device" style="left:{dev.cx - dev.w / 2 - 9:.0f}px;top:{dev.cy - dev.h / 2 - 9:.0f}px;width:{dev.w:.0f}px;height:{dev.h:.0f}px;
        padding:9px;border-radius:43px;transform:rotate({f['device']['rot']}deg)"><img src="{shot}" style="width:{dev.w:.0f}px;height:{dev.h:.0f}px;border-radius:34px"></div>
      {pops}{sparkles}"""
    style = css("phone")
    page = (f"<!doctype html><html lang=en><meta charset=utf-8><style>{style}"
            f"html,body{{width:1024px;height:500px}} .pop{{border-width:4px;border-radius:14px}}"
            f".badge{{font-size:19px;padding:7px 14px 7px 10px;gap:7px;border-width:1.5px}}</style><body>{background(1024, 500, 3)}{body}</body></html>")
    chrome_png(page, 1024, 500, IMAGES / "featureGraphic.png").save(IMAGES / "featureGraphic.png", optimize=True)
    print("wrote featureGraphic.png")


def icon_png() -> None:
    # The full square; Play masks the corners itself, as launchers do.
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "icon.html"
        page.write_text(f'<html><body style="margin:0"><img src="{(HERE / "icon" / "icon.svg").as_uri()}" width="512" height="512" '
                        f'style="display:block"></body></html>', encoding="utf-8")
        shot = Path(tmp) / "icon.png"
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--window-size=512,512", "--default-background-color=00000000", "--allow-file-access-from-files",
                        f"--screenshot={shot}", page.as_uri()], check=True, capture_output=True, timeout=120)
        Image.open(shot).convert("RGBA").save(IMAGES / "icon.png", optimize=True)
    print("wrote icon.png")


if __name__ == "__main__":
    jobs = {
        "phone": lambda: render_set("phone", ["phoneScreenshots"]),
        "tablet": lambda: render_set("tablet", ["sevenInchScreenshots", "tenInchScreenshots"]),
        "feature": feature,
        "icon": icon_png,
    }
    for name in sys.argv[1:] or jobs:
        jobs[name]()
