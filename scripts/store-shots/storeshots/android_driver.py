"""Android capture driver: walks one target's shot plan on the container emulator (adb on port 5038 via
scripts/with-container-adb.sh) and writes `<shot id>.png` raws.

Per shot: reseed the demo databases (android-seed.sh, so every shot starts from the same state), launch the app,
dismiss the debug build's beta notice, switch to the shot's workspace through the toolbar's "More options" menu,
perform the shot's action, let the WebView settle, and take a screencap. Actions:

- `verse_menu <bookmark id>`: tap the verse number of the bookmark's first verse -> the verse's bookmark popup.
- `note <bookmark id>`: verse_menu, then tap the bookmark card's note text -> the formatted note view.
- `label_dialog <bookmark id>`: verse_menu, then the card's primary label chip -> "Bookmark labels", then
  "Select or edit labels…" -> the label assignment screen.
- `book_chooser`: tap the toolbar's reference title -> the book chooser sheet.
- `workspaces`: open the Workspaces sheet.
- `search <query>`: drawer "Find", type the query (creates the index on first use).

Bookmark ids, verse references and note texts come from demo-content/<lang>/content.yaml, so the recipes do not
depend on the Bible text's language. The UI texts used as anchors are in UI_TEXT (English, with their resource keys);
phase 2 resolves them per language from the app's string resources.
"""
from __future__ import annotations

import html
import re
import subprocess
import sys
import time
from pathlib import Path

import yaml

from .android_actions import NodeNotFound, find_node

PKG = "net.bible.android.activity.debug"

# English anchors: (text, resource key) — app/src/main/res/values/strings.xml or bibleview-js default.yaml.
UI_TEXT = {
    "more_options": "More options",            # reading_toolbar_options (content-desc)
    "workspaces_entry": r"Workspaces.*",       # menu entry "Workspaces …"
    "beta_dismiss": "Dismiss until next update",  # beta_notice_dismiss_until_update
    "close": "Close",                          # content-desc of sheet close buttons
    "edit_labels": "Select or edit labels…",   # bibleview-js assignLabelsMenuEntry1
    "menu": "Menu",                            # drawer button (content-desc)
    "find": "Find",                            # drawer entry / search field
    "create_index": "Create",                  # index creation dialog
    "bookmark_popup": "Bookmark",              # first action of the verse popup
}


class Device:
    def __init__(self, repo: Path, pkg: str = PKG):
        self.repo = repo
        self.pkg = pkg

    def adb(self, *args: str, check: bool = True) -> str:
        cmd = [str(self.repo / "scripts/with-container-adb.sh"), "adb", *args]
        return subprocess.run(cmd, check=check, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout

    def shell(self, cmd: str, check: bool = True) -> str:
        return self.adb("shell", cmd, check=check)

    def dump(self) -> str:
        for _ in range(3):
            out = self.shell("uiautomator dump /sdcard/ss-dump.xml >/dev/null && cat /sdcard/ss-dump.xml", check=False)
            if "<hierarchy" in out:
                return out[out.index("<?xml") if "<?xml" in out else out.index("<hierarchy"):]
            time.sleep(1)
        raise RuntimeError("uiautomator dump failed")

    def tap(self, x: int, y: int) -> None:
        self.shell(f"input tap {x} {y}")

    def wait_for(self, timeout: float = 30, **keys) -> tuple[int, int]:
        end = time.monotonic() + timeout
        last: Exception | None = None
        while time.monotonic() < end:
            try:
                return find_node(self.dump(), **keys)
            except NodeNotFound as e:
                last = e
            time.sleep(1)
        raise NodeNotFound(f"timeout after {timeout}s: {last}")

    def tap_node(self, timeout: float = 30, **keys) -> None:
        self.tap(*self.wait_for(timeout, **keys))

    def screencap(self, out: Path) -> None:
        out.parent.mkdir(parents=True, exist_ok=True)
        data = subprocess.run([str(self.repo / "scripts/with-container-adb.sh"), "adb", "exec-out", "screencap", "-p"],
                              check=True, capture_output=True, stdin=subprocess.DEVNULL).stdout
        out.write_bytes(data)


def prepare_device(dev: Device, locale_tag: str, landscape: bool) -> None:
    """Locale, fixed status bar (demo mode), orientation."""
    dev.shell(f"cmd locale set-app-locales {dev.pkg} --locales {locale_tag}", check=False)
    dev.shell("settings put global sysui_demo_allowed 1")
    for extra in ("-e command enter",
                  "-e command clock -e hhmm 0941",
                  "-e command battery -e level 100 -e plugged false",
                  "-e command network -e wifi show -e level 4 -e fully true",
                  "-e command network -e mobile show -e datatype none -e level 4 -e fully true",
                  "-e command notifications -e visible false"):
        dev.shell(f"am broadcast -a com.android.systemui.demo {extra}")
    lock_rotation(dev, landscape)


def lock_rotation(dev: Device, landscape: bool) -> None:
    """Rotate the emulated device itself (`adb emu rotate`, auto-rotate on) until the display is landscape or
    portrait. A window-manager rotation lock does not hold: every `uiautomator dump` unfreezes it again."""
    dev.shell("settings put system accelerometer_rotation 1")
    want = "landscape" if landscape else "portrait"
    for _ in range(5):
        # Current size, not the rotation number: a pixel_tablet's natural orientation is already landscape.
        out = dev.shell("dumpsys window displays 2>/dev/null | grep -m1 -o 'cur=[0-9]*x[0-9]*'", check=False)
        m = re.search(r"cur=(\d+)x(\d+)", out)
        if m and (int(m.group(1)) > int(m.group(2))) == landscape:
            return
        dev.adb("emu", "rotate", check=False)
        time.sleep(3)
    raise RuntimeError(f"display did not rotate to {want}")


def set_night(dev: Device, night: bool) -> None:
    dev.shell(f"cmd uimode night {'yes' if night else 'no'}")


def plain(note_html: str) -> str:
    text = re.sub(r"<[^>]+>", " ", note_html or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


class Content:
    def __init__(self, path: Path):
        self.data = yaml.safe_load(Path(path).read_text())
        self.bookmarks = {b["id"]: b for b in self.data.get("bookmarks", [])}
        self.labels = {l["id"]: l for l in self.data.get("labels", [])}

    def bookmark(self, bid: str) -> dict:
        if bid not in self.bookmarks:
            raise KeyError(f"no bookmark {bid!r} in content.yaml")
        return self.bookmarks[bid]

    def first_verse(self, bid: str) -> int:
        return int(self.bookmark(bid)["ref"].split("-")[0].split(".")[-1])

    def note_prefix(self, bid: str, n: int = 24) -> str:
        return plain(self.bookmark(bid).get("note", ""))[:n]

    def primary_label_name(self, bid: str) -> str:
        b = self.bookmark(bid)
        lid = b.get("primaryLabel") or b["labels"][0]
        return self.labels[lid]["name"]


def verse_number_node(xml: str, verse: int) -> tuple[int, int]:
    """The verse number of `verse` in the reading WebView: a node below the toolbar whose text is exactly the
    number, directly followed by a text node (the verse text); footnote markers are letters, not numbers."""
    import xml.etree.ElementTree as ET
    nodes = list(ET.fromstring(xml.encode()).iter("node"))
    from .android_actions import _centre
    toolbar = 0
    for n in nodes:
        if n.attrib.get("content-desc") == UI_TEXT["more_options"]:
            toolbar = int(re.findall(r"-?\d+", n.attrib["bounds"])[3])
    for i, n in enumerate(nodes[:-1]):
        if n.attrib.get("text") == str(verse):
            nxt = nodes[i + 1].attrib.get("text", "")
            x, y = _centre(n.attrib["bounds"])
            if len(nxt.strip()) >= 2 and y > toolbar:
                return x + 30, y
    raise NodeNotFound(f"verse number {verse}")


def node_bounds(xml: str, text_re: str) -> tuple[int, int, int, int]:
    import xml.etree.ElementTree as ET
    pattern = re.compile(text_re, re.S)
    for n in ET.fromstring(xml.encode()).iter("node"):
        if pattern.fullmatch(n.attrib.get("text", "")):
            return tuple(map(int, re.findall(r"-?\d+", n.attrib["bounds"])))  # type: ignore[return-value]
    raise NodeNotFound(f"no node matching {text_re!r}")


def open_app(dev: Device) -> None:
    """Launch and wait until the reading toolbar shows with no dialog over it in two dumps 3 s apart (the beta
    notice of debug builds appears a moment after the toolbar)."""
    dev.shell(f"monkey -p {dev.pkg} -c android.intent.category.LAUNCHER 1", check=False)
    end = time.monotonic() + 90
    calm = 0
    while time.monotonic() < end:
        xml = dev.dump()
        try:
            dev.tap(*find_node(xml, text=UI_TEXT["beta_dismiss"]))
            calm = 0
            time.sleep(1.5)
            continue
        except NodeNotFound:
            pass
        try:
            find_node(xml, desc=UI_TEXT["more_options"])
            calm += 1
            if calm >= 2:
                return
        except NodeNotFound:
            calm = 0
        time.sleep(3)
    raise NodeNotFound("reading toolbar did not appear")


def switch_workspace(dev: Device, name: str) -> None:
    dev.tap_node(desc=UI_TEXT["more_options"])
    dev.tap_node(text_re=UI_TEXT["workspaces_entry"])
    end = time.monotonic() + 20
    while time.monotonic() < end:
        xml = dev.dump()
        try:
            dev.tap(*find_node(xml, text=name))
            time.sleep(1)
            return
        except NodeNotFound:
            pass
        try:
            find_node(xml, text=f"{name} (current)")
            dev.tap(*find_node(xml, desc=UI_TEXT["close"]))
            time.sleep(1)
            return
        except NodeNotFound:
            time.sleep(1)
    raise NodeNotFound(f"workspace {name!r} not in the Workspaces sheet")


def verse_menu(dev: Device, content: Content, bid: str) -> None:
    verse = content.first_verse(bid)
    end = time.monotonic() + 30
    while True:
        try:
            dev.tap(*verse_number_node(dev.dump(), verse))
            break
        except NodeNotFound:
            if time.monotonic() > end:
                raise
            time.sleep(1)
    dev.wait_for(text=UI_TEXT["bookmark_popup"])


def perform(dev: Device, content: Content, action: str, arg: str | None) -> None:
    if action == "none":
        return
    if action == "verse_menu":
        verse_menu(dev, content, arg)
    elif action == "note":
        verse_menu(dev, content, arg)
        prefix = content.note_prefix(arg)
        # Tap the note's first line (top left of its node): the node can span the label chips below it.
        x1, y1, _, _ = node_bounds(dev.dump(), r"\s*" + re.escape(prefix) + ".*")
        dev.tap(x1 + 60, y1 + 20)
    elif action == "label_dialog":
        verse_menu(dev, content, arg)
        name = content.primary_label_name(arg)
        dev.tap_node(text_re=r"\s*" + re.escape(name) + r"\s*")
        dev.tap_node(text_re=r"\s*" + re.escape(UI_TEXT["edit_labels"]) + r"\s*")
    elif action == "book_chooser":
        # The reference title is the first text node of the toolbar (e.g. "Jeremiah 9:23").
        xml = dev.dump()
        dev.tap(*find_node(xml, text_re=r".*\d+:\d+"))
    elif action == "workspaces":
        dev.tap_node(desc=UI_TEXT["more_options"])
        dev.tap_node(text_re=UI_TEXT["workspaces_entry"])
    elif action == "search":
        dev.tap_node(desc=UI_TEXT["menu"])
        dev.tap_node(text=UI_TEXT["find"])
        dev.tap_node(text=UI_TEXT["find"])
        dev.shell(f"input text {arg or 'love'}")
        dev.shell("input keyevent 66")
        time.sleep(3)
        try:
            dev.tap(*find_node(dev.dump(), text=UI_TEXT["create_index"]))
            time.sleep(60)
        except NodeNotFound:
            time.sleep(5)
    else:
        raise ValueError(f"unknown action {action}")


def run_plan(repo: Path, plan: dict, content_path: Path, demo_zip: Path, out_dir: Path, landscape: bool,
             settle: float = 5.0) -> list[str]:
    dev = Device(repo)
    content = Content(content_path)
    prepare_device(dev, plan["lang"], landscape)
    done, failed = [], []
    for night in (False, True):
        shots = [s for s in plan["shots"] if s["night"] == night]
        if not shots:
            continue
        set_night(dev, night)
        for s in shots:
            try:
                subprocess.run([str(repo / "scripts/store-shots/android-seed.sh"), str(demo_zip), dev.pkg],
                               check=True, capture_output=True, stdin=subprocess.DEVNULL)
                lock_rotation(dev, landscape)
                open_app(dev)
                switch_workspace(dev, s["workspace"])
                time.sleep(settle)
                perform(dev, content, s["action"], s["action_arg"])
                time.sleep(settle)
                dev.screencap(out_dir / f"{s['id']}.png")
                done.append(s["id"])
                print(f"android: {s['id']}", flush=True)
            except Exception as e:  # noqa: BLE001 - record and continue; the set stays incomplete
                failed.append(s["id"])
                dev.screencap(out_dir / "failed" / f"{s['id']}.png")
                (out_dir / "failed" / f"{s['id']}.xml").write_text(dev.dump())
                print(f"android: FAILED {s['id']}: {e}", file=sys.stderr, flush=True)
    set_night(dev, False)
    if failed:
        print(f"android: failed shots: {', '.join(failed)}", file=sys.stderr)
    return done


def main(argv: list[str]) -> int:
    import argparse
    import json
    p = argparse.ArgumentParser(prog="android_driver")
    p.add_argument("--plan", required=True)
    p.add_argument("--content", required=True)
    p.add_argument("--zip", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--landscape", action="store_true")
    a = p.parse_args(argv)
    from .state import repo_root
    plan = json.loads(Path(a.plan).read_text())
    done = run_plan(repo_root(), plan, Path(a.content), Path(a.zip), Path(a.out), a.landscape)
    return 0 if len(done) == len(plan["shots"]) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
