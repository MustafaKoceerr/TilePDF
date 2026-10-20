"""Drives an emulator for the store screenshots, through adb and uiautomator.

  python -I device.py push              copy samples/*.pdf to /sdcard/Download/Books
  python -I device.py demo              clean status bar: 12:00, full battery, no notifications
  python -I device.py dump              visible texts and descriptions with their centres
  python -I device.py tap <text>        tap the first node whose text or description contains <text>
  python -I device.py xy <x> <y>        tap a point
  python -I device.py swipe x1 y1 x2 y2 [ms]
  python -I device.py key <KEYCODE>
  python -I device.py shot <name>       save raw/<name>.png
  python -I device.py sh <args...>      adb shell

The serial comes from $DEVICE (default emulator-5554).
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

HERE = Path(__file__).resolve().parent
ADB = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")) / "platform-tools" / "adb.exe"
SERIAL = os.environ.get("DEVICE", "emulator-5554")


def adb(*args: str, binary: bool = False):
    result = subprocess.run([str(ADB), "-s", SERIAL, *args], capture_output=True, timeout=120)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def nodes():
    # A dump taken while the screen changes can fail; try again a few times.
    for _ in range(5):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            break
        time.sleep(1)
    # The dump is our own device's UI; it has no DTD or entities.
    root = ElementTree.fromstring(xml[xml.index("<"):])
    for node in root.iter("node"):
        label = node.get("text") or node.get("content-desc") or ""
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        yield label, ((x1 + x2) // 2, (y1 + y2) // 2), node


def tap_node(node_centre) -> None:
    adb("shell", "input", "tap", str(node_centre[0]), str(node_centre[1]))


def open_document(name: str) -> None:
    """From the library: Open PDF, then Downloads/Books/<name> in the system picker."""
    # The floating button's label isn't in the accessibility tree; it sits bottom right
    # (FAB=x,y for other screens; the default is a 1080x2424 phone).
    fab_x, fab_y = os.environ.get("FAB", "960,2241").split(",")
    adb("shell", "input", "tap", fab_x, fab_y)
    time.sleep(3)
    for _ in range(8):
        found = list(nodes())
        labels = {label: centre for label, centre, _ in found}
        if name in labels:
            tap_node(labels[name])
            time.sleep(6)
            print("opened", name)
            return
        if "List view" in labels:
            tap_node(labels["List view"])
        elif "Files in Books" not in labels and "Books" in labels:
            # The folder in Downloads, not the breadcrumb at the top.
            folder = [c for label, c, _ in found if label == "Books" and c[1] > 500]
            tap_node(folder[0] if folder else labels["Books"])
        else:
            adb("shell", "input", "swipe", "540", "1900", "540", "900", "400")
        time.sleep(2)
    sys.exit(f"couldn't find {name}")


def signature_strokes(left: int, top: int, width: int, height: int):
    """ "Alice Liddell" in the single-stroke Hershey script font (public domain), as a pen would write it,
    slanted a little, fitted into the box."""
    from HersheyFonts import HersheyFonts

    font = HersheyFonts()
    font.load_default_font("scripts")
    lines = list(font.lines_for_text("Alice Liddell"))
    points = [p for line in lines for p in line]
    min_x, max_x = min(p[0] for p in points), max(p[0] for p in points)
    min_y, max_y = min(p[1] for p in points), max(p[1] for p in points)
    scale = min(width / (max_x - min_x), height / (max_y - min_y))

    def at(x, y):
        # Hershey's y grows downward, as on screen; lean the tops forward a little.
        x = x - min_x + 0.18 * (max_y - y)
        return int(left + x * scale * 0.9), int(top + (y - min_y) * scale)

    # Join consecutive segments into strokes, so each pen-down draws a whole letter.
    strokes, current = [], []
    for (a, b) in lines:
        if current and current[-1] == a:
            current.append(b)
        else:
            if current:
                strokes.append(current)
            current = [a, b]
    strokes.append(current)
    return [[at(*p) for p in stroke] for stroke in strokes]


def sign(left: int, top: int, width: int, height: int) -> None:
    for stroke in signature_strokes(left, top, width, height):
        (x, y), rest = stroke[0], stroke[1:]
        commands = [f"input motionevent DOWN {x} {y}"]
        commands += [f"input motionevent MOVE {px} {py}" for px, py in rest]
        commands.append(f"input motionevent UP {rest[-1][0]} {rest[-1][1]}")
        adb("shell", "; ".join(commands))
    print("signed")


def main(cmd: str, *args: str) -> None:
    if cmd == "push":
        adb("shell", "mkdir", "-p", "/sdcard/Download/Books")
        for pdf in sorted((HERE / "samples").glob("*.pdf")):
            adb("push", str(pdf), "/sdcard/Download/Books/")
        print(adb("shell", "ls", "/sdcard/Download/Books"))
    elif cmd == "demo":
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        for command in (["enter"], ["clock", "-e", "hhmm", "1200"], ["battery", "-e", "level", "100", "-e", "plugged", "false"],
                        ["network", "-e", "wifi", "show", "-e", "level", "4"],
                        ["network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4"],
                        ["notifications", "-e", "visible", "false"]):
            adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", *command)
        print("demo mode on")
    elif cmd == "dump":
        for label, centre, _ in nodes():
            if label:
                print(label[:90], centre)
    elif cmd == "tap":
        # An exact label first: "x.pdf" must not hit "Preview the file x.pdf".
        found = list(nodes())
        match = next((n for n in found if n[0] == args[0]), None) or next((n for n in found if args[0] in n[0]), None)
        if match is None:
            sys.exit(f"not found: {args[0]}")
        label, (x, y), _ = match
        adb("shell", "input", "tap", str(x), str(y))
        print("tapped", label[:60], x, y)
    elif cmd == "open":
        open_document(args[0])
    elif cmd == "sign":
        sign(*map(int, args))
    elif cmd == "xy":
        adb("shell", "input", "tap", *args)
    elif cmd == "swipe":
        adb("shell", "input", "swipe", *args[:4], args[4] if len(args) > 4 else "400")
    elif cmd == "key":
        adb("shell", "input", "keyevent", args[0])
    elif cmd == "shot":
        out = HERE / "raw" / f"{args[0]}.png"
        out.parent.mkdir(exist_ok=True)
        out.write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print("saved", out.name)
    elif cmd == "sh":
        print(adb("shell", *args))
    elif cmd == "wait":
        time.sleep(float(args[0]))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main(*sys.argv[1:])
