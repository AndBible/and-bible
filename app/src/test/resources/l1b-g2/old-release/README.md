# G2 old-release fixtures

Files written by a RELEASED AndBible build running on the in-container Android emulator
(API 36, emulator 37.3.4), driven through the app UI. Nothing here was produced by current-branch code.

## Source build

- Release `production-1117` (Release 5.1.1117, "Latest", 2026-08-29), asset `andbible-production-1117.apk`
  (standard appearance, github distribution; drawer footer: `Version 5.1.1117#385a9b4 github standard release`).
- Download: read-only `gh release download production-1117 -R AndBible/and-bible`.
- Tag commit: `385a9b424b4f7cafeaade2e9f246784ed7c2b6bb`.
- APK SHA-256: `1c9ed93391642c0de4529065a65e10eb44242d60722231b917b14c5919cdf796`
- Package `net.bible.android.activity`, versionCode 1117. Installed with `adb install -r -d`.
- Manifests below all carry `andBibleVersion` 1117 (manifestVersion 1).

## Scenario

Fresh install (`pm clear`), KJV module pushed to the app's external files dir (module initials `KJV`,
"King James Version (1769) with Strongs Numbers and Morphology", from the container's test module set).
The first run created the app's built-in sample data (sample bookmarks and the default labels).
On top of that, via the UI: created label `G2Fixture`; created two verse bookmarks, John 3:20 (KJV ordinal
33099) and John 3:21 (ordinal 33100), both labelled `G2Fixture`; added a bookmark note
`G2 fixture bookmark note` to John 3:20; added a StudyPad text entry `G2 fixture studypad entry`
to the `G2Fixture` StudyPad. Backups were saved with Backup & Restore -> "Phone storage" (SAF, Downloads)
and pulled with `adb pull`.

## Files

| File | App action | SHA-256 | Contents |
|------|-----------|---------|----------|
| `db-backup.abdb.zip` | Backup & Restore -> Database -> Backup to -> Phone storage (default name `AndBibleDatabaseBackup.abdb.zip`) | `c0acf1ef083780ee13e56bae4d02311e4efadc8011cb0a1e505c1f089b5efed9` | Manifest `{"backupType":"DB_BACKUP","contains":["BOOKMARKS","WORKSPACES","READINGPLANS","REPOSITORIES","SETTINGS","MYDOCUMENTS","AI_SETTINGS"],...}`; entries `db/bookmarks.sqlite3`, `readingplans`, `workspaces`, `repositories`, `settings`, `ai_settings`, `mydocuments`, `progress` (`.sqlite3`). Bookmarks DB: 14 BibleBookmark rows (12 sample + 2 ours), 2 BibleBookmarkNotes (1 sample + ours), 15 BibleBookmarkToLabel, 1 StudyPadTextEntry. Labels: Blue, G2Fixture, Green, Red, Salvation, Underline, plus the special `__PARAGRAPH_BREAK_LABEL__`, `__SPEAK_LABEL__`, `__UNLABELED__`. |
| `module-backup.abmd.zip` | Backup & Restore -> Documents -> Backup to -> Phone storage, module KJV only (default name `AndBibleModulesBackup.abmd.zip`) | `786977ce3b4d5e7dac5a491166e250ec42933fd326e351cbd286ddb4a16ec9aa` | Manifest `{"backupType":"MODULE_BACKUP","contains":null,...}`; `mods.d/kjv.conf` and `modules/texts/ztext/kjv/*` (module initials `KJV`). |
| `studypad-export.abdb.zip` | Study Pads -> overflow -> Export Study Pads -> label `G2Fixture` -> Phone storage (default name `G2Fixture.abdb.zip`) | `4841fbcd8d59052f2783c1a223d95f67880ae87ec196d684c86776658707b0c8` | Manifest `{"backupType":"STUDYPAD_EXPORT","contains":["BOOKMARKS"],...}`; single entry `db/bookmarks.sqlite3`: 1 label (`G2Fixture`), 2 BibleBookmark (John 3:20 with note, John 3:21), 1 StudyPadTextEntry. |

Verified with `python3 -I` zipfile (`testzip()` clean; manifest entry present).
