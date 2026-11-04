"""Compare committed migration records with the pinned Swift export (not a UI oracle)."""
import io
import pathlib
import sqlite3
import sys
import tempfile
import zipfile


def rows(path, table, columns):
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
        return sorted(db.execute(f'SELECT {columns} FROM "{table}"').fetchall(), key=repr)


def compare(data, fixture):
    databases = list(pathlib.Path(data).rglob("bookmarks.sqlite3"))
    databases = [p for p in databases if p.parent.name == "databases"]
    assert len(databases) == 1, databases
    root = databases[0].parent
    with zipfile.ZipFile(fixture) as source:
        with zipfile.ZipFile(io.BytesIO(source.read("oracle/AndBibleDatabaseBackup.abdb.zip"))) as oracle:
            with tempfile.TemporaryDirectory(prefix="checklist-3945-oracle-") as directory:
                for name, table, columns in [
                    ("bookmarks", "BibleBookmark", "id,ordinalStart,ordinalEnd,startOffset,endOffset"),
                    ("bookmarks", "BibleBookmarkNotes", "bookmarkId,notes"),
                    ("bookmarks", "StudyPadTextEntry", "id,labelId,orderNumber,indentLevel"),
                    ("bookmarks", "StudyPadTextEntryText", "studyPadTextEntryId,text"),
                    ("workspaces", "Workspace", "id,name"),
                    ("workspaces", "PageManager", "windowId,currentCategoryName"),
                ]:
                    expected = pathlib.Path(directory) / f"{name}.sqlite3"
                    expected.write_bytes(oracle.read(f"db/{name}.sqlite3"))
                    actual_rows = rows(root / f"{name}.sqlite3", table, columns)
                    expected_rows = rows(expected, table, columns)
                    assert actual_rows == expected_rows, f"{table}: migrated/oracle mismatch ({len(actual_rows)}/{len(expected_rows)})"
                    print(f"PASS migration oracle {table}: {len(actual_rows)} records, selected fields byte-equal")


if __name__ == "__main__":
    compare(*sys.argv[1:])
