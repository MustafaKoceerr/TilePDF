#!/usr/bin/env python3
"""Generates the PDFs used by the instrumented tests.

The files are small hand-built PDFs with known content and positions, so tests
can assert exact coordinates. Output is deterministic: running this again
reproduces the committed files byte for byte.

    python3 tools/generate_test_pdfs.py

Writes to core/testing/src/main/assets/:
- sample.pdf     3 pages (A4, A4, A4 landscape): a heading and a black
                 rectangle per page; rendering, tiles and text positions.
- text-dense.pdf 80 A4 pages of dense text; text-provider tests.
- encrypted.pdf  1 A4 page, RC4 40-bit (R2); user password "secret",
                 owner password "owner"; password handling.
- links.pdf      3 pages; page 1 links to page 3, a web URI and a
                 javascript: URI; links.
- outline.pdf    12 pages with an outline covering XYZ, Fit, named and GoTo
                 destinations and UTF-16 Turkish titles; table of contents.
- annotated.pdf  2 pages. Page 1: a foreign highlight (/NM acrobat-1) with a
                 popup, a foreign square without /NM, and a TilePDF ink
                 annotation (/NM tilepdf-fixture-ink), each with an
                 appearance stream. Page 2 has /Rotate 90; annotations.
- forms.pdf      1 A4 page with an AcroForm: text "name", multiline text
                 "notes", check box "agree" (export Yes), radio group
                 "size" (S, L), combo box "city" (Ankara, İstanbul, İzmir)
                 and read-only text "id" (value 42); form filling.
- calc.pdf       1 A4 page with form scripts: text fields "a" and "b", a
                 read-only "total" calculated as their sum (AFSimple_Calculate)
                 and formatted with two decimals (AFNumber_Format), and "age",
                 whose validate script alerts "Too old" and rejects values over
                 150; form JavaScript.
- xfa.pdf        A dynamic XFA form (NeedsRendering): a Letter page with a
                 caption "Name" and a text field "name" at 1 in, 1.5 in, 3 in
                 wide, and a check box "agree" below it. Its PDF page only
                 says "Please wait"; XFA forms.
- scanned.pdf    1 A4 page that is only a 200 dpi grey image of three lines
                 of text (no text objects); text recognition. Needs Pillow,
                 and draws with the app's Noto Sans subset.
"""

import hashlib
import os
import random

ASSETS = os.path.join(os.path.dirname(__file__), "..", "core", "testing", "src", "main", "assets")
FONT = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"


def write_pdf(objects, catalog_id, name, trailer_extra="", binary=None):
    """Serializes numbered objects (1-based, in list order) with an xref table.
    [binary] maps object numbers to raw bodies, for streams that aren't text."""
    out = b"%PDF-1.4\n"
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(out))
        raw = (binary or {}).get(number)
        out += f"{number} 0 obj\n".encode() + (raw if raw is not None else body.encode("latin-1")) + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    out += "".join(f"{offset:010d} 00000 n \n" for offset in offsets).encode()
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root {catalog_id} 0 R{trailer_extra} >>\n"
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


# Standard security handler, revision 2 (PDF 1.7 spec, algorithms 3.1-3.4).
PASSWORD_PAD = bytes.fromhex("28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A")


def rc4(key, data):
    state = list(range(256))
    j = 0
    for i in range(256):
        j = (j + state[i] + key[i % len(key)]) % 256
        state[i], state[j] = state[j], state[i]
    out = bytearray()
    i = j = 0
    for byte in data:
        i = (i + 1) % 256
        j = (j + state[i]) % 256
        state[i], state[j] = state[j], state[i]
        out.append(byte ^ state[(state[i] + state[j]) % 256])
    return bytes(out)


def padded(password):
    return (password.encode("latin-1") + PASSWORD_PAD)[:32]


def encrypted():
    # One A4 page like sample.pdf, encrypted with RC4 40-bit.
    user, owner, permissions = "secret", "owner", -4
    file_id = hashlib.md5(b"tilepdf encrypted.pdf").digest()
    o_value = rc4(hashlib.md5(padded(owner)).digest()[:5], padded(user))
    key = hashlib.md5(
        padded(user) + o_value + permissions.to_bytes(4, "little", signed=True) + file_id
    ).digest()[:5]
    u_value = rc4(key, PASSWORD_PAD)

    def encrypt_stream(number, content):
        object_key = hashlib.md5(key + number.to_bytes(3, "little") + bytes(2)).digest()[:10]
        data = rc4(object_key, content.encode("latin-1")).decode("latin-1")
        return f"<< /Length {len(data)} >>\nstream\n{data}\nendstream"

    objects = [
        FONT,
        encrypt_stream(2, "BT /F1 36 Tf 72 742 Td (Encrypted page) Tj ET"),
        "<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] "
        "/Resources << /Font << /F1 1 0 R >> >> /Contents 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Catalog /Pages 4 0 R >>",
        f"<< /Filter /Standard /V 1 /R 2 /O <{o_value.hex()}> /U <{u_value.hex()}> /P {permissions} >>",
    ]
    write_pdf(objects, 5, "encrypted.pdf",
              f" /Encrypt 6 0 R /ID [<{file_id.hex()}> <{file_id.hex()}>]")


def text_dense():
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
    write_pdf(objects, len(objects), "text-dense.pdf")


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


def links():
    # Page 1 has three link annotations: a GoTo to page 3 (XYZ, 200 pt from
    # its top), a web URI and a javascript: URI the reader must not follow.
    def text(label, y):
        return f"BT /F1 24 Tf 80 {y} Td ({label}) Tj ET"
    page1 = stream("\n".join([
        text("Go to page 3", 738), text("Website", 638), text("Script", 538),
    ]))
    page = ("<< /Type /Page /Parent 11 0 R /MediaBox [0 0 595 842] "
            "/Resources << /Font << /F1 1 0 R >> >> /Contents {content} 0 R{annots} >>")
    objects = [
        FONT,
        page1,
        page.format(content=2, annots=" /Annots [8 0 R 9 0 R 10 0 R]"),
        stream(text("Page 2", 742)),
        page.format(content=4, annots=""),
        stream(text("Page 3", 742)),
        page.format(content=6, annots=""),
        "<< /Type /Annot /Subtype /Link /Rect [72 730 320 760] /Border [0 0 0] /Dest [7 0 R /XYZ 0 642 0] >>",
        "<< /Type /Annot /Subtype /Link /Rect [72 630 320 660] /Border [0 0 0] "
        "/A << /S /URI /URI (https://example.com/tilepdf) >> >>",
        "<< /Type /Annot /Subtype /Link /Rect [72 530 320 560] /Border [0 0 0] "
        "/A << /S /URI /URI (javascript:alert\\(1\\)) >> >>",
        "<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>",
        "<< /Type /Catalog /Pages 11 0 R >>",
    ]
    write_pdf(objects, 12, "links.pdf")


def form(bbox, content, resources=""):
    """A form XObject, as used for annotation appearance streams."""
    return (f"<< /Type /XObject /Subtype /Form /BBox [{bbox}]{resources} /Length {len(content)} >>\n"
            f"stream\n{content}\nendstream")


def annotated():
    # Annotation order on page 1 matters: the square has no /NM, so its key is
    # its index, #2 (highlight, popup, square, ink).
    page = ("<< /Type /Page /Parent 13 0 R /MediaBox [0 0 595 842] "
            "/Resources << /Font << /F1 1 0 R >> >> /Contents {content} 0 R{extra} >>")
    objects = [
        FONT,
        stream("BT /F1 18 Tf 72 742 Td (Highlighted words) Tj ET"),
        page.format(content=2, extra=" /Annots [6 0 R 7 0 R 8 0 R 9 0 R]"),
        stream("BT /F1 18 Tf 72 742 Td (Rotated) Tj ET"),
        page.format(content=4, extra=" /Rotate 90"),
        "<< /Type /Annot /Subtype /Highlight /NM (acrobat-1) /Contents (Foreign note) /F 4 /C [1 1 0] "
        "/QuadPoints [72 760 260 760 72 736 260 736] /Rect [72 736 260 760] /Popup 7 0 R /AP << /N 10 0 R >> >>",
        "<< /Type /Annot /Subtype /Popup /Parent 6 0 R /Rect [300 700 500 800] /Open false >>",
        "<< /Type /Annot /Subtype /Square /F 4 /C [0 0 1] /BS << /W 2 >> /Rect [300 300 400 400] "
        "/AP << /N 11 0 R >> >>",
        "<< /Type /Annot /Subtype /Ink /NM (tilepdf-fixture-ink) /F 4 /C [1 0 0] /Border [0 0 2] "
        "/InkList [[100 500 200 500 200 400]] /Rect [99 399 201 501] /AP << /N 12 0 R >> >>",
        form("72 736 260 760", "/GS gs 1 1 0 rg 72 736 188 24 re f",
             " /Resources << /ExtGState << /GS << /BM /Multiply >> >> >>"),
        form("300 300 400 400", "0 0 1 RG 2 w 301 301 98 98 re S"),
        form("99 399 201 501", "1 0 0 RG 2 w 1 J 1 j 100 500 m 200 500 l 200 400 l S"),
        "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
        "<< /Type /Catalog /Pages 13 0 R >>",
    ]
    write_pdf(objects, 14, "annotated.pdf")


def forms():
    # Rects are in PDF user space (bottom-left origin); tests read them back
    # in top-left points (y' = 842 - y).
    widget = "/Type /Annot /Subtype /Widget /F 4 /P 3 0 R"
    objects = [
        FONT,
        stream("BT /F1 12 Tf 72 760 Td (Form) Tj ET"),
        "<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 1 0 R >> >> "
        "/Contents 2 0 R /Annots [7 0 R 8 0 R 9 0 R 11 0 R 12 0 R 13 0 R 14 0 R] >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Catalog /Pages 4 0 R /AcroForm 6 0 R >>",
        "<< /Fields [7 0 R 8 0 R 9 0 R 10 0 R 13 0 R 14 0 R] /DR << /Font << /Helv 1 0 R >> >> "
        "/DA (/Helv 12 Tf 0 g) /NeedAppearances true >>",
        f"<< {widget} /FT /Tx /T (name) /Rect [72 700 300 724] /DA (/Helv 12 Tf 0 g) >>",
        f"<< {widget} /FT /Tx /T (notes) /Ff 4096 /Rect [72 600 300 680] /DA (/Helv 12 Tf 0 g) >>",
        f"<< {widget} /FT /Btn /T (agree) /V /Off /AS /Off /Rect [72 560 86 574] "
        "/AP << /N << /Yes 15 0 R /Off 16 0 R >> >> >>",
        "<< /FT /Btn /Ff 49152 /T (size) /V /Off /Kids [11 0 R 12 0 R] >>",
        f"<< {widget} /Parent 10 0 R /AS /Off /Rect [72 520 86 534] /AP << /N << /S 17 0 R /Off 16 0 R >> >> >>",
        f"<< {widget} /Parent 10 0 R /AS /Off /Rect [120 520 134 534] /AP << /N << /L 17 0 R /Off 16 0 R >> >> >>",
        f"<< {widget} /FT /Ch /Ff 131072 /T (city) /Opt [(Ankara) {utf16('İstanbul')} {utf16('İzmir')}] "
        "/Rect [72 470 200 490] /DA (/Helv 12 Tf 0 g) >>",
        f"<< {widget} /FT /Tx /T (id) /Ff 1 /V (42) /Rect [72 430 200 450] /DA (/Helv 12 Tf 0 g) >>",
        form("0 0 14 14", "0 g 2 2 10 10 re f"),
        form("0 0 14 14", "0 G 0.5 0.5 13 13 re S"),
        form("0 0 14 14", "0 g 4 4 6 6 re f"),
    ]
    write_pdf(objects, 5, "forms.pdf")


def calc():
    # Balanced parentheses need no escaping in PDF literal strings.
    widget = "/Type /Annot /Subtype /Widget /F 4 /P 3 0 R /DA (/Helv 12 Tf 0 g)"
    sum_script = 'AFSimple_Calculate("SUM", new Array("a", "b"));'
    format_script = 'AFNumber_Format(2, 0, 0, 0, "", true);'
    age_script = 'if (event.value > 150) { app.alert("Too old"); event.rc = false; }'
    objects = [
        FONT,
        stream("BT /F1 12 Tf 72 760 Td (Calculations) Tj ET"),
        "<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 1 0 R >> >> "
        "/Contents 2 0 R /Annots [7 0 R 8 0 R 9 0 R 10 0 R] >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Catalog /Pages 4 0 R /AcroForm 6 0 R >>",
        "<< /Fields [7 0 R 8 0 R 9 0 R 10 0 R] /CO [9 0 R] /DR << /Font << /Helv 1 0 R >> >> "
        "/DA (/Helv 12 Tf 0 g) >>",
        f"<< {widget} /FT /Tx /T (a) /Rect [72 700 200 724] >>",
        f"<< {widget} /FT /Tx /T (b) /Rect [72 660 200 684] >>",
        f"<< {widget} /FT /Tx /T (total) /Ff 1 /Rect [72 620 200 644] "
        f"/AA << /C << /S /JavaScript /JS ({sum_script}) >> /F << /S /JavaScript /JS ({format_script}) >> >> >>",
        f"<< {widget} /FT /Tx /T (age) /Rect [72 580 200 604] /AA << /V << /S /JavaScript /JS ({age_script}) >> >> >>",
    ]
    write_pdf(objects, 5, "calc.pdf")


XFA_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<xdp:xdp xmlns:xdp="http://ns.adobe.com/xdp/">
<config xmlns="http://www.xfa.org/schema/xci/3.1/"><acrobat><acrobat7><dynamicRender>required</dynamicRender></acrobat7></acrobat></config>
<template xmlns="http://www.xfa.org/schema/xfa-template/3.3/">
<subform name="form1" layout="tb" locale="en_US">
<pageSet><pageArea name="Page1" id="Page1"><contentArea x="0in" y="0in" w="8.5in" h="11in"/>
<medium stock="letter" short="8.5in" long="11in"/></pageArea></pageSet>
<subform name="main" w="8.5in" h="11in">
<field name="name" x="1in" y="1.5in" w="3in" h="0.4in"><ui><textEdit/></ui>
<font typeface="Helvetica" size="12pt"/>
<caption reserve="0.8in"><value><text>Name</text></value></caption></field>
<field name="agree" x="1in" y="2.2in" w="2in" h="0.3in"><ui><checkButton/></ui>
<caption reserve="1in"><value><text>Agree</text></value></caption>
<items><integer>1</integer><integer>0</integer></items></field>
</subform>
</subform>
</template>
<xfa:datasets xmlns:xfa="http://www.xfa.org/schema/xfa-data/1.0/"><xfa:data><form1><main><name/><agree>0</agree></main></form1></xfa:data></xfa:datasets>
</xdp:xdp>"""


def xfa():
    objects = [
        FONT,
        stream("BT /F1 18 Tf 72 720 Td (Please wait) Tj ET"),
        "<< /Type /Page /Parent 4 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 1 0 R >> >> "
        "/Contents 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Catalog /Pages 4 0 R /AcroForm 6 0 R /NeedsRendering true >>",
        "<< /Fields [] /XFA 7 0 R >>",
        stream(XFA_TEMPLATE),
    ]
    write_pdf(objects, 5, "xfa.pdf")


SCANNED_LINES = ["Taranmış bir sayfa", "Metin tanıma örneği", "İstanbul Şişli 2026"]


def scanned():
    import zlib
    from PIL import Image, ImageDraw, ImageFont

    dpi = 200
    width, height = 595 * dpi // 72, 842 * dpi // 72
    image = Image.new("L", (width, height), 255)
    draw = ImageDraw.Draw(image)
    font_path = os.path.join(os.path.dirname(__file__), "..", "core", "pdf", "src", "main", "assets", "fonts",
                             "NotoSans-TilePdf.ttf")
    font = ImageFont.truetype(font_path, 24 * dpi // 72)
    for i, line in enumerate(SCANNED_LINES):
        draw.text((72 * dpi // 72, (100 + 48 * i) * dpi // 72), line, fill=0, font=font)
    pixels = zlib.compress(image.tobytes(), 9)
    image_object = (f"<< /Type /XObject /Subtype /Image /Width {width} /Height {height} /ColorSpace /DeviceGray "
                    f"/BitsPerComponent 8 /Filter /FlateDecode /Length {len(pixels)} >>\nstream\n")
    objects = [
        "image",
        stream("q 595 0 0 842 0 0 cm /Im1 Do Q"),
        "<< /Type /Page /Parent 4 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im1 1 0 R >> >> "
        "/Contents 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Catalog /Pages 4 0 R >>",
    ]
    write_pdf(objects, 5, "scanned.pdf", binary={1: image_object.encode("latin-1") + pixels + b"\nendstream"})


if __name__ == "__main__":
    sample()
    text_dense()
    outline()
    encrypted()
    links()
    annotated()
    forms()
    calc()
    xfa()
    scanned()
