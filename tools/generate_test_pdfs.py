#!/usr/bin/env python3
"""Generates the PDFs used by the instrumented tests.

The files are small hand-built PDFs with known content and positions, so tests
can assert exact coordinates. Output is deterministic: running this again
reproduces the committed files byte for byte.

    python3 tools/generate_test_pdfs.py

Writes to app/src/androidTest/assets/:
- sample.pdf     3 pages (A4, A4, A4 landscape): a heading and a black
                 rectangle per page; rendering, tiles and text positions.
- benchmark.pdf  80 A4 pages of dense text; text-provider benchmark.
- outline.pdf    12 pages with an outline covering XYZ, Fit, named and GoTo
                 destinations and UTF-16 Turkish titles; table of contents.
"""

import os
import random

ASSETS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "androidTest", "assets")
FONT = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"


def write_pdf(objects, catalog_id, name):
    """Serializes numbered objects (1-based, in list order) with an xref table."""
    out = b"%PDF-1.4\n"
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n{body}\nendobj\n".encode("latin-1")
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    out += "".join(f"{offset:010d} 00000 n \n" for offset in offsets).encode()
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root {catalog_id} 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    with open(os.path.join(ASSETS, name), "wb") as f:
        f.write(out)
    print(f"{name}: {len(out)} bytes")


def stream(content):
    return f"<< /Length {len(content)} >>\nstream\n{content}\nendstream"


def kids(page_ids):
    return " ".join(f"{p} 0 R" for p in page_ids)


def sample():
    # 36 pt heading at x 72 with its baseline 100 pt below the top of each
    # page, and a 200 x 100 pt black rectangle near the bottom left.
    sizes = [(595, 842), (595, 842), (842, 595)]
    objects = [FONT]
    pages_id = len(objects) + 1 + 2 * len(sizes)
    page_ids = []
    for index, (width, height) in enumerate(sizes):
        objects.append(stream(
            f"0 0 0 rg 50 50 200 100 re f BT /F1 36 Tf 72 {height - 100} Td "
            f"(TilePDF sample page {index + 1}) Tj ET"
        ))
        objects.append(
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {width} {height}] "
            f"/Resources << /Font << /F1 1 0 R >> >> /Contents {len(objects)} 0 R >>"
        )
        page_ids.append(len(objects))
    objects.append(f"<< /Type /Pages /Kids [{kids(page_ids)}] /Count {len(page_ids)} >>")
    objects.append(f"<< /Type /Catalog /Pages {pages_id} 0 R >>")
    write_pdf(objects, len(objects), "sample.pdf")


def benchmark():
    # 45 lines of 8-12 words per page, from a fixed vocabulary and seed.
    rng = random.Random(42)
    words = ("the a of render engine tile cache memory page zoom bitmap viewport scroll "
             "coordinate mapper search text layer thread matrix point pixel draft level "
             "screen android compose document library position strategy provider native").split()
    page_count = 80
    objects = [FONT]
    pages_id = len(objects) + 1 + 2 * page_count
    page_ids = []
    for _ in range(page_count):
        lines = [" ".join(rng.choice(words) for _ in range(rng.randint(8, 12))) for _ in range(45)]
        ops = ["BT /F1 11 Tf 14 TL 56 780 Td"] + [f"({line}) Tj T*" for line in lines] + ["ET"]
        objects.append(stream("\n".join(ops)))
        objects.append(
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 595 842] "
            f"/Resources << /Font << /F1 1 0 R >> >> /Contents {len(objects)} 0 R >>"
        )
        page_ids.append(len(objects))
    objects.append(f"<< /Type /Pages /Kids [{kids(page_ids)}] /Count {page_count} >>")
    objects.append(f"<< /Type /Catalog /Pages {pages_id} 0 R >>")
    write_pdf(objects, len(objects), "benchmark.pdf")


def utf16(text):
    """A PDF text string in UTF-16BE with a byte order mark, as hex."""
    return "<FEFF" + text.encode("utf-16-be").hex().upper() + ">"


def outline():
    page_count = 12
    # Each outline target gets a visible heading bar at its y, so a jump can
    # be checked by eye.
    headings = {
        0: [(742, "1. Intro"), (400, "1.1 Motivation")],
        3: [(742, "2. Render engine")],
        4: [(600, "2.1 Tiles")],
        8: [(300, "3. Search")],
    }
    objects = [FONT]
    content_ids, page_ids = [], []
    for page in range(page_count):
        ops = [f"BT /F1 14 Tf 72 800 Td (Page {page + 1}) Tj ET"]
        for y, text in headings.get(page, []):
            ops.append(f"0 0 0 rg 56 {y - 30} 480 30 re f BT 1 1 1 rg /F1 22 Tf 64 {y - 24} Td ({text}) Tj ET")
        objects.append(stream("\n".join(ops)))
        content_ids.append(len(objects))
        objects.append(None)  # page, filled once the Pages id is known
        page_ids.append(len(objects))

    def add(body=None):
        objects.append(body)
        return len(objects)

    pages_id = add()
    outline_id, o1, o11, o2, o21, o3 = (add() for _ in range(6))
    catalog_id = add()

    def page_ref(index):
        return f"{page_ids[index]} 0 R"

    for page in range(page_count):
        objects[page_ids[page] - 1] = (
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 595 842] "
            f"/Resources << /Font << /F1 1 0 R >> >> /Contents {content_ids[page]} 0 R >>"
        )
    objects[pages_id - 1] = f"<< /Type /Pages /Kids [{kids(page_ids)}] /Count {page_count} >>"
    objects[outline_id - 1] = f"<< /Type /Outlines /First {o1} 0 R /Last {o3} 0 R /Count 5 >>"
    # XYZ destination with a y position, and a Turkish title.
    objects[o1 - 1] = (f"<< /Title {utf16('1. Giriş')} /Parent {outline_id} 0 R /Next {o2} 0 R "
                       f"/First {o11} 0 R /Last {o11} 0 R /Count 1 /Dest [{page_ref(0)} /XYZ 0 742 0] >>")
    objects[o11 - 1] = (f"<< /Title {utf16('1.1 Motivasyon ve Amaç')} /Parent {o1} 0 R "
                        f"/Dest [{page_ref(0)} /XYZ 0 400 0] >>")
    # Fit destination: no y, top of the page.
    objects[o2 - 1] = (f"<< /Title (2. Render Motoru) /Parent {outline_id} 0 R /Prev {o1} 0 R /Next {o3} 0 R "
                       f"/First {o21} 0 R /Last {o21} 0 R /Count 1 /Dest [{page_ref(3)} /Fit] >>")
    # Named destination, resolved through the catalog's /Dests name tree.
    objects[o21 - 1] = f"<< /Title (2.1 Karolar) /Parent {o2} 0 R /Dest (tiles) >>"
    # GoTo action instead of /Dest.
    objects[o3 - 1] = (f"<< /Title {utf16('3. Arama ve Işaretleme')} /Parent {outline_id} 0 R /Prev {o2} 0 R "
                       f"/A << /S /GoTo /D [{page_ref(8)} /XYZ 0 300 0] >> >>")
    objects[catalog_id - 1] = (
        f"<< /Type /Catalog /Pages {pages_id} 0 R /Outlines {outline_id} 0 R /PageMode /UseOutlines "
        f"/Names << /Dests << /Names [(tiles) [{page_ref(4)} /XYZ 0 600 0]] >> >> >>"
    )
    write_pdf(objects, catalog_id, "outline.pdf")


if __name__ == "__main__":
    sample()
    benchmark()
    outline()
