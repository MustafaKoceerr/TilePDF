"""Builds the sample PDFs the store screenshots show, from public-domain texts.

Sources (downloaded into .cache/, which isn't committed):
- Project Gutenberg #11, Alice's Adventures in Wonderland (text only),
- #114, John Tenniel's illustrations for it (1865),
- #1342 Pride and Prejudice, #84 Frankenstein, #132 The Art of War, #2701 Moby-Dick.
All are in the public domain in the US; the Project Gutenberg name and license
text are left out, as their license asks when the trademark isn't used.

Run: python -I make_samples.py   (writes samples/*.pdf, named as a reader would name them)
"""
import html
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageFilter, ImageOps

HERE = Path(__file__).resolve().parent
CACHE = HERE / ".cache" / "gutenberg"
OUT = HERE / "samples"
FONTS = (HERE / "fonts").as_uri()
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

BOOK_CSS = f"""
@font-face {{ font-family: Literata; src: url({FONTS}/Literata.ttf); font-style: normal; font-weight: 200 900; }}
@font-face {{ font-family: Literata; src: url({FONTS}/Literata-Italic.ttf); font-style: italic; font-weight: 200 900; }}
@font-face {{ font-family: Jakarta; src: url({FONTS}/PlusJakartaSans.ttf); font-weight: 200 800; }}
@page {{ size: 5.5in 8.5in; margin: 0.75in 0.7in 0.8in;
  @bottom-center {{ content: counter(page); font: 9pt Literata; color: #8a8178; }} }}
@page cover {{ margin: 0; @bottom-center {{ content: none; }} }}
* {{ -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
html {{ font: 10.5pt/1.55 Literata, serif; color: #24201c; hyphens: auto; text-align: justify; }}
p {{ margin: 0; text-indent: 1.3em; }}
h2 + p, .fig + p, p.first {{ text-indent: 0; }}
.chapter {{ break-before: page; }}
.chapter .num {{ font-weight: 500; font-size: 10pt; letter-spacing: .18em; text-transform: uppercase;
  text-align: center; color: #7a5c9e; margin: 0.6in 0 0.12in; }}
.chapter h2 {{ display: block; font-size: 17pt; text-align: center; color: #24201c; font-style: italic;
  font-weight: 400; margin: 0; }}
.chapter h2::after {{ content: "\\2766"; display: block; font-size: 12pt; color: #b8a6cf; margin: .14in 0 .25in; }}
.chapter > p.first::first-letter {{ float: left; font-size: 3.4em; line-height: .85; padding: .06em .08em 0 0; color: #6650a4; }}
.fig {{ text-align: center; margin: .18in 0; break-inside: avoid; }}
.fig img {{ max-width: 72%; max-height: 2.5in; mix-blend-mode: multiply; }}
.fig .cap {{ font-size: 8.5pt; font-style: italic; color: #8a8178; margin-top: .05in; text-indent: 0; text-align: center; }}
pre, .poem {{ font: italic 9.5pt/1.5 Literata; text-indent: 0; margin: .12in 0 .12in 1.2em; white-space: pre-wrap; text-align: left; }}
.toc {{ break-before: page; }}
.toc .head {{ font-weight: 400; font-style: italic; font-size: 18pt; text-align: center; margin: .5in 0 .3in; }}
.toc ol {{ list-style: none; padding: 0; margin: 0; }}
.toc li {{ display: flex; gap: .4em; padding: .055in 0; border-bottom: 1px dotted #d8cfe6; }}
.toc li span:first-child {{ color: #7a5c9e; width: 2.4em; }}
"""


def chrome_pdf(html_text: str, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(html_text, encoding="utf-8")
        subprocess.run(
            [CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", "--generate-pdf-document-outline",
             "--allow-file-access-from-files", f"--print-to-pdf={out}", page.as_uri()],
            check=True, capture_output=True, timeout=180,
        )
    print("wrote", out.name, out.stat().st_size // 1024, "KB")


def cover(title: str, author: str, line: str, colors: tuple[str, str], art: str = "") -> str:
    """A full-bleed cover page: gradient, title, author; art is an <img> or ''."""
    a, b = colors
    return f"""
<section style="page: cover; height: 8.5in; width: 5.5in; box-sizing: border-box; padding: .8in .6in;
  background: linear-gradient(160deg, {a}, {b}); color: #fff; display: flex; flex-direction: column;
  text-align: center; font-family: Literata; hyphens: none;">
  <div style="font: 600 8pt Jakarta; letter-spacing: .3em; text-transform: uppercase; opacity: .8;">{line}</div>
  <div style="flex: 1; display: flex; align-items: center; justify-content: center;">{art}</div>
  <div style="font-weight: 500; font-size: 30pt; line-height: 1.1;">{title}</div>
  <div style="width: .6in; height: 2px; background: rgba(255,255,255,.6); margin: .22in auto;"></div>
  <div style="font: italic 13pt Literata; opacity: .92;">{author}</div>
</section>"""


# --- Alice --------------------------------------------------------------------------------------

def tenniel_images() -> list[tuple[Path, str]]:
    """The illustrations in book order, with their descriptions from #114."""
    page = (CACHE / "114-h.htm").read_text(encoding="utf-8", errors="replace")
    found = re.findall(r'SRC="images/([^"]+)"[^>]*ALT="([^"]*)"', page, flags=re.I)
    if not found:
        found = [(m[1], m[0]) for m in re.findall(r'ALT="([^"]*)"[^>]*SRC="images/([^"]+)"', page, flags=re.I)]
    return [(CACHE / "tenniel" / name, html.unescape(alt)) for name, alt in found]


def alice_chapters() -> list[tuple[str, str, list[str]]]:
    """(number, title, blocks) per chapter; blocks are <p>/<pre> HTML."""
    page = (CACHE / "alice11" / "pg11-images.html").read_text(encoding="utf-8")
    body = page.split('<div class="chapter">', 1)[1]
    body = body.split('pg-footer', 1)[0]
    chapters = []
    for part in body.split('<div class="chapter">'):
        head = re.search(r"<h2>.*?(CHAPTER [IVXL]+)\.<br>\s*(.*?)</h2>", part, flags=re.S)
        blocks = re.findall(r"<p[^>]*>.*?</p>|<pre>.*?</pre>", part, flags=re.S)
        blocks = [b for b in blocks if "pg-" not in b and "THE END" not in b.upper() or "THE END" in b.upper()]
        chapters.append(("Chapter " + head.group(1).split()[1], " ".join(head.group(2).split()), blocks))
    return chapters


STOP = set("with from into that this they their alice alice's there have being looking holding".split())


def place_images(chapters, images):
    """Puts each illustration after the paragraph that best matches its description, in book order."""
    flat = [(ci, bi, re.sub(r"<[^>]+>", " ", b).lower()) for ci, (_, _, bs) in enumerate(chapters) for bi, b in enumerate(bs)]
    total, pos, placed = len(flat), 0, {}
    for k, (img, alt) in enumerate(images):
        words = [w for w in re.findall(r"[a-z']+", alt.lower()) if len(w) > 3 and w not in STOP]
        target = int(total * (k + 0.5) / len(images))
        end = min(total, max(target, pos) + total // 8)
        best, best_score = None, 0
        for i in range(pos, end):
            score = sum(w.rstrip("s") in flat[i][2] for w in words)
            if score > best_score:
                best, best_score = i, score
        i = best if best is not None else max(pos, target)
        i = min(i, total - 1)
        placed.setdefault((flat[i][0], flat[i][1]), []).append((img, alt))
        pos = i + 1
    return placed


def make_alice() -> None:
    images = tenniel_images()
    frontispiece, inline = images[0], images[1:]
    chapters = alice_chapters()
    placed = place_images(chapters, inline)
    rabbit = next((p for p, alt in inline if "Rabbit" in alt and "watch" in alt), inline[0][0])
    art = f'<img src="{rabbit.as_uri()}" style="max-height: 3.4in; max-width: 3.4in; filter: invert(1); mix-blend-mode: screen; opacity: .9;">'
    parts = [cover("Alice’s Adventures in Wonderland", "Lewis Carroll", "Illustrated by John Tenniel", ("#6650a4", "#2e2a6e"), art)]
    parts.append(f"""<section style="break-before: page; text-align: center; hyphens: none;">
      <img src="{frontispiece[0].as_uri()}" style="max-width: 82%; max-height: 4.4in; mix-blend-mode: multiply; margin-top: .3in;">
      <div style="font-style: italic; font-size: 20pt; margin: .3in 0 .05in;">Alice’s Adventures in Wonderland</div>
      <div style="font-size: 11pt;">Lewis Carroll</div>
      <div style="font-size: 9pt; color: #8a8178; margin-top: .08in;">With forty-two illustrations by John Tenniel</div></section>""")
    toc = "".join(f"<li><span>{n.split()[1]}</span><span>{t}</span></li>" for n, t, _ in chapters)
    parts.append(f'<section class="toc"><div class="head">Contents</div><ol>{toc}</ol></section>')
    for ci, (number, title, blocks) in enumerate(chapters):
        body = []
        for bi, block in enumerate(blocks):
            if bi == 0 and block.startswith("<p"):
                block = block.replace("<p", '<p class="first"', 1) if "class=" not in block[:12] else block
            body.append(block)
            for img, alt in placed.get((ci, bi), []):
                body.append(f'<div class="fig"><img src="{img.as_uri()}" alt="{html.escape(alt)}"><p class="cap">{html.escape(alt)}</p></div>')
        parts.append(f'<section class="chapter"><div class="num">{number}</div><h2>{title}</h2>{"".join(body)}</section>')
    chrome_pdf(f"<!doctype html><html lang=en><meta charset=utf-8><title>Alice’s Adventures in Wonderland</title>"
               f"<style>{BOOK_CSS}</style>{''.join(parts)}</html>", OUT / "Alice in Wonderland.pdf")


# --- Short classics for the library ---------------------------------------------------------------

def gutenberg_text(number: int) -> str:
    text = (CACHE / f"pg{number}.txt").read_text(encoding="utf-8-sig")
    text = text.split("*** START OF", 1)[1].split("***", 1)[1]
    return text.split("*** END OF", 1)[0].replace("\r\n", "\n")


def paragraphs(text: str) -> list[str]:
    return [" ".join(p.split()) for p in re.split(r"\n\s*\n", text) if p.strip()]


CLASSICS = [
    # file, title, author, line, colors, gutenberg number, chapters as (start, stop, heading)
    ("Pride and Prejudice.pdf", "Pride and Prejudice", "Jane Austen", "A novel", ("#b5476b", "#5e2142"), 1342,
     [("Chapter I.]", "CHAPTER II.", "Chapter I|Chapter One"), ("CHAPTER II.", "CHAPTER III.", "Chapter II|Chapter Two")]),
    ("Frankenstein.pdf", "Frankenstein", "Mary Shelley", "or, The Modern Prometheus", ("#2f5d62", "#152c33"), 84,
     [("Letter 1\n", "Letter 2\n", "To Mrs. Saville, England|Letter 1"), ("Letter 2\n", "Letter 3\n", "To Mrs. Saville, England|Letter 2")]),
    ("The Art of War.pdf", "The Art of War", "Sun Tzŭ", "Translated by Lionel Giles", ("#a2662a", "#4a2a12"), 132,
     [("I. LAYING PLANS", "II. WAGING WAR", "Chapter I|Laying Plans"), ("II. WAGING WAR", "III. ATTACK BY STRATAGEM", "Chapter II|Waging War")]),
    ("Moby-Dick.pdf", "Moby-Dick", "Herman Melville", "or, The Whale", ("#2b5d9a", "#0f2747"), 2701,
     [("CHAPTER 1. Loomings.", "CHAPTER 2. The Carpet-Bag.", "Chapter 1|Loomings")]),
]


def find_after_contents(text: str, marker: str) -> int:
    """The second occurrence: the first is usually the table of contents."""
    first = text.index(marker)
    try:
        return text.index(marker, first + len(marker))
    except ValueError:
        return first


def monogram(title: str) -> str:
    """The title's initial in a thin double ring: a quiet cover ornament."""
    return f"""<svg width="2.3in" height="2.3in" viewBox="0 0 100 100" style="opacity: .85">
      <circle cx="50" cy="50" r="46" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width=".8"/>
      <circle cx="50" cy="50" r="41" fill="none" stroke="#fff" stroke-opacity=".3" stroke-width=".5"/>
      <text x="50" y="66" text-anchor="middle" font-family="Literata" font-style="italic" font-size="46" fill="#fff">{title[0]}</text>
    </svg>"""


def make_classics() -> None:
    for file, title, author, line, colors, number, chapters in CLASSICS:
        text = gutenberg_text(number)
        sections = []
        for start, stop, heading in chapters:
            i = find_after_contents(text, start)
            j = text.index(stop, i + len(start))
            # A translator's note in brackets doesn't open the chapter.
            paras = [p for p in paragraphs(text[i:j])[1:] if not p.startswith("[")]
            body = "".join(f'<p{" class=first" if k == 0 else ""}>{html.escape(p)}</p>' for k, p in enumerate(paras))
            number_line, _, chapter_title = heading.partition("|")
            sections.append(f'<section class="chapter"><div class="num">{number_line}</div><h2>{chapter_title}</h2>{body}</section>')
        chrome_pdf(f"<!doctype html><html lang=en><meta charset=utf-8><title>{title}</title><style>{BOOK_CSS}</style>"
                   f"{cover(title, author, line, colors, monogram(title))}{''.join(sections)}</html>", OUT / file)


# --- A scanned page, for text recognition -------------------------------------------------------

def make_scan() -> None:
    text = gutenberg_text(2701)
    i = find_after_contents(text, "CHAPTER 1. Loomings.")
    paras = paragraphs(text[i:])[1:4]
    page_html = f"""<!doctype html><meta charset=utf-8><style>
      @font-face {{ font-family: Literata; src: url({FONTS}/Literata.ttf); }}
      body {{ margin: 0; width: 1240px; height: 1754px; background: #fff; }}
      .page {{ padding: 150px 150px; font: 33px/1.5 Literata; color: #1d1d1d; text-align: justify; hyphens: auto; }}
      h2 {{ text-align: center; font-weight: 500; font-size: 38px; margin: 40px 0 50px; letter-spacing: .08em; }}
      p {{ margin: 0; text-indent: 1.4em; }}
      .no {{ text-align: center; margin-top: 60px; font-size: 26px; }}
    </style><div class=page lang=en><h2>CHAPTER I.<br>LOOMINGS.</h2>
    {''.join(f'<p>{html.escape(p)}</p>' for p in paras)}<div class=no>1</div></div>"""
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "scan.html"
        src.write_text(page_html, encoding="utf-8")
        png = Path(tmp) / "scan.png"
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--window-size=1240,1754", "--allow-file-access-from-files", f"--screenshot={png}", src.as_uri()],
                       check=True, capture_output=True, timeout=120)
        page = Image.open(png).convert("L")
    # Paper: warm tint, a slight tilt, soft blur and grain, as a phone scan of a book page looks.
    page = page.rotate(-0.8, resample=Image.BICUBIC, expand=False, fillcolor=255).filter(ImageFilter.GaussianBlur(0.6))
    noise = Image.effect_noise(page.size, 18).filter(ImageFilter.GaussianBlur(0.8))
    page = Image.blend(page, ImageOps.autocontrast(noise), 0.06)
    scan = ImageOps.colorize(page, black="#2a2620", white="#f3ecdc")
    scan.save(OUT / "Scanned page.pdf", "PDF", resolution=150)
    print("wrote Scanned page.pdf")


# --- A form to fill in and sign -------------------------------------------------------------------

def make_form() -> None:
    """A fictional library's membership form with real AcroForm fields."""
    from reportlab.lib.colors import HexColor, white
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfgen import canvas

    violet, ink, muted, line = HexColor("#6650a4"), HexColor("#1f1b24"), HexColor("#6b6474"), HexColor("#cfc6dc")
    out = OUT / "Membership form.pdf"
    c = canvas.Canvas(str(out), pagesize=A4)
    c.setTitle("Library Membership Application")
    w, h = A4
    form = c.acroForm

    c.setFillColor(violet)
    c.rect(0, h - 130, w, 130, stroke=0, fill=1)
    c.setFillColor(white)
    c.setFont("Helvetica-Bold", 10)
    c.drawString(48, h - 52, "WONDERLAND PUBLIC LIBRARY")
    c.setFont("Helvetica-Bold", 26)
    c.drawString(48, h - 92, "Membership Application")

    y = h - 175

    def label(text, x, y):
        c.setFillColor(muted)
        c.setFont("Helvetica", 8.5)
        c.drawString(x, y, text.upper())

    def text_field(name, caption, x, y, width):
        label(caption, x, y + 26)
        form.textfield(name=name, tooltip=caption, x=x, y=y, width=width, height=22, borderColor=line, fillColor=HexColor("#f7f4fb"),
                       textColor=ink, fontName="Helvetica", fontSize=11, borderWidth=1, forceBorder=True)

    c.setFillColor(ink)
    c.setFont("Helvetica-Bold", 13)
    c.drawString(48, y, "About you")
    y -= 50
    text_field("full_name", "Full name", 48, y, w - 96)
    y -= 56
    text_field("email", "Email", 48, y, (w - 110) / 2)
    text_field("phone", "Phone", 62 + (w - 110) / 2, y, (w - 110) / 2)
    y -= 56
    text_field("address", "Street address", 48, y, w - 96)
    y -= 56
    text_field("city", "City", 48, y, (w - 110) / 2)
    text_field("postcode", "Postcode", 62 + (w - 110) / 2, y, (w - 110) / 2)

    y -= 50
    c.setFillColor(ink)
    c.setFont("Helvetica-Bold", 13)
    c.drawString(48, y, "Membership")
    y -= 30
    for k, kind in enumerate(["Adult", "Student", "Senior"]):
        x = 48 + k * 150
        form.radio(name="membership", tooltip="Membership", value=kind.lower(), x=x, y=y - 4, size=16, buttonStyle="circle",
                   borderColor=violet, fillColor=white, textColor=violet, selected=False, forceBorder=True)
        c.setFillColor(ink)
        c.setFont("Helvetica", 11)
        c.drawString(x + 24, y, kind)

    y -= 44
    c.setFont("Helvetica-Bold", 13)
    c.drawString(48, y, "I like to read")
    y -= 30
    for k, topic in enumerate(["Fiction", "History", "Science", "Poetry"]):
        x = 48 + (k % 2) * 225
        yy = y - (k // 2) * 28
        form.checkbox(name="likes_" + topic.lower(), tooltip=topic, x=x, y=yy - 4, size=16, buttonStyle="check",
                      borderColor=violet, fillColor=white, textColor=violet, checked=False, forceBorder=True)
        c.setFillColor(ink)
        c.setFont("Helvetica", 11)
        c.drawString(x + 24, yy, topic)

    y -= 80
    form.checkbox(name="agree_rules", tooltip="I agree to the library rules", x=48, y=y - 4, size=16, buttonStyle="check", borderColor=violet,
                  fillColor=white, textColor=violet, checked=False, forceBorder=True)
    c.setFillColor(ink)
    c.setFont("Helvetica", 11)
    c.drawString(72, y, "I agree to look after the books I borrow and return them on time.")

    y -= 60
    label("Signature", 48, y + 6)
    c.setStrokeColor(line)
    c.line(48, y - 46, 330, y - 46)
    text_field("date", "Date", 360, y - 46, w - 408)

    c.setFillColor(muted)
    c.setFont("Helvetica", 8)
    c.drawString(48, 40, "A sample form for demonstration. Wonderland Public Library is fictional.")
    c.save()
    print("wrote", out.name)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    make_alice()
    make_classics()
    make_scan()
    make_form()
