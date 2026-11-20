#!/usr/bin/env python3
"""Rebuilds collection-sample/.../and-bible/manna.sqlite3, the sample collection file.

A real collection is made in the app (label + bookmarks, then "Export Study Pads"; see docs/addon-modules.md).
This script writes an equivalent Room bookmark database without the app, so the sample is reproducible and
reviewable: two labels ("Comfort", a verse list with one note, and "John study", a StudyPad with a text entry and a
paragraph break), 10 Bible bookmarks from John and the Psalms. The tables come from the latest Room schema export.

Usage: python3 docs/addon-samples/make_collection_sample.py
"""
import json, os, sqlite3, uuid

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(os.path.dirname(here))
schema_dir = os.path.join(root, "sharedData/schemas/net.bible.android.database.BookmarkDatabase")
latest = max(int(f.split(".")[0]) for f in os.listdir(schema_dir))
schema = json.load(open(os.path.join(schema_dir, f"{latest}.json")))["database"]
target = os.path.join(here, "collection-sample/modules/genbook/rawgenbook/mannasample/and-bible/manna.sqlite3")

# KJV ordinals (JSword numbering, as stored in BibleBookmark.kjvOrdinalStart/End), checked with the app's versification.
ORDINALS = {
    "John 1:1": 27089,
    "John 3:16": 27182,
    "John 14:27": 27752,
    "John 15:5": 27762,
    "John 16:33": 27818,
    "Ps 23:1": 14758,
    "Ps 23:4": 14761,
    "Ps 46:1": 15160,
    "Ps 91:1": 15986,
    "Ps 121:1": 16702,
}

def uid(n):  # fixed, readable UUIDs: the ids are derived per module on import, but stay stable across re-exports
    return uuid.UUID(f"5a8c1c70-0000-4d2e-9a10-{n:012x}").bytes

COMFORT, STUDY = uid(1), uid(2)
PARAGRAPH_BREAK = bytes.fromhex("AAAAAAAAAAAA4AAA8AAAAAAAAAAAAAAA")
COMFORT_VERSES = ["Ps 23:1", "Ps 23:4", "Ps 46:1", "Ps 91:1", "Ps 121:1", "John 14:27"]
STUDY_VERSES = ["John 1:1", "John 3:16", "John 15:5", "John 16:33"]  # a paragraph break follows John 3:16
NOTES = {"Ps 23:4": "A verse to learn by heart: the shadow is passed through, not lived in."}
MARKER = 0xFF2E7D32 - (1 << 32)  # signed ARGB, green
MARKER2 = 0xFF1565C0 - (1 << 32)

def main():
    os.makedirs(os.path.dirname(target), exist_ok=True)
    if os.path.exists(target):
        os.remove(target)
    db = sqlite3.connect(target)
    db.execute("PRAGMA foreign_keys=OFF")
    for e in schema["entities"]:
        db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    for v in schema.get("views", []):
        db.execute(v["createSql"].replace("${VIEW_NAME}", v["viewName"]))
    for q in schema["setupQueries"]:
        db.execute(q)

    db.execute("INSERT INTO Label (id, name, color, type) VALUES (?, 'Comfort', ?, NULL)", (COMFORT, MARKER))
    db.execute("INSERT INTO Label (id, name, color, type) VALUES (?, 'John study', ?, 'EXAMPLE')", (STUDY, MARKER2))
    db.execute("INSERT INTO Label (id, name) VALUES (?, '__PARAGRAPH_BREAK_LABEL__')", (PARAGRAPH_BREAK,))

    n = 100
    ids = {}
    def bookmark(ref, label, order, primary):
        nonlocal n
        n += 1
        b = uid(n)
        ids[(ref, label)] = b
        o = ORDINALS[ref]
        db.execute(
            "INSERT INTO BibleBookmark (kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, id, createdAt, "
            "primaryLabelId, lastUpdatedOn, wholeVerse) VALUES (?, ?, ?, ?, 'KJV', ?, ?, ?, ?, 1)",
            (o, o, o, o, b, 1795000000000 + n, primary, 1795000000000 + n))
        db.execute("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber) VALUES (?, ?, ?)", (b, label, order))
        if ref in NOTES:
            db.execute("INSERT INTO BibleBookmarkNotes (bookmarkId, notes) VALUES (?, ?)", (b, NOTES[ref]))
        return b

    for i, ref in enumerate(COMFORT_VERSES):
        bookmark(ref, COMFORT, -1, COMFORT)
    order = 0
    entry = uid(900)
    db.execute("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (?, ?, 0, 0)", (entry, STUDY))
    db.execute("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (?, ?)",
               (entry, "<p>Four verses on abiding and peace, in the order Jesus spoke them.</p>"))
    order = 1
    for ref in STUDY_VERSES:
        b = bookmark(ref, STUDY, order, STUDY)
        order += 1
        if ref == "John 3:16":
            db.execute("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber) VALUES (?, ?, ?)",
                       (b, PARAGRAPH_BREAK, -1))
    db.execute(f"PRAGMA user_version={latest}")
    db.commit()
    db.close()
    print(f"wrote {target} (schema {latest})")

if __name__ == "__main__":
    main()
