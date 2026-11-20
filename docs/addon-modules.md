# Making add-on modules for AndBible

An **add-on module** is a SWORD module with `Category=And Bible` that adds something to the app
instead of text: fonts, CSS, reading plans, AI prompt packs, a background image, or (since #4162)
**bookmark collections**. Users install them from a module repository like any other document, and
document sync moves them between devices.

This guide is for authors. Working samples are in [`docs/addon-samples/`](addon-samples/README.md).
User-facing documentation is in `website/content/en/docs/`.

## What every add-on module has

```
[MyAddon]                                   ; module initials; keep them unique
DataPath=./modules/genbook/rawgenbook/myaddon/
Version=1.0                                 ; see "Versions and updates"
Description=Shown in the document list
About=Longer text
DistributionLicense=Public Domain           ; or the real license
Category=And Bible
ModDrv=RawGenBook
InstallSize=12345                           ; bytes, informative
AndBibleMinimumVersion=1278                 ; app build number, see below
AndBibleProvides...=...                     ; one or more keys, see the kinds below
```

- The module is a zip: `mods.d/MyAddon.conf` plus the `DataPath` folder with the files it provides.
  The files an add-on key names are relative to `DataPath`; keep them relative and without
  `..` (fonts and collections are rejected otherwise).
- **`AndBibleMinimumVersion`** is the app *build number* (the last digits of the version, e.g. 5.0.1278 ->
  1278). An app older than that neither offers nor installs the module. Use the first build that supports
  the kind (collections: the build that contains #4162).
- **`Version`** is how an installed module is recognised as updated. Always bump it when you re-publish
  (see below).

## The kinds

| Kind | conf key | File |
|---|---|---|
| Font | `AndBibleProvidesFont=Family;path/to/font.ttf` (repeatable) | TrueType file |
| Style | `AndBibleCSS=path/to/style.css` | CSS prefixed `.sword-INITIALS` |
| Reading plan | `AndBibleProvidesReadingPlan=Plan.properties` (+ `AndBibleReadingPlanDateBased=true`) | `.properties` |
| Prompt pack | `AndBibleProvidesPrompts=path/prompts.csv` | Prompt CSV |
| Background image | `AndBibleProvidesBackgroundImage=Name;path/image.jpg` | Image |
| **Collection** | `AndBibleProvidesLabels=path/collection.sqlite3` | Label export database |

Fonts, styles and plans are described in the website docs
(`customisation/custom_css.md`, `customisation/module_creation.md`, `reading_plans.md`). One module may
combine kinds.

## Prompt packs

`AndBibleProvidesPrompts` names a CSV file in the same format the app exports from *AI prompts*
(semicolon-separated, header row, fields quoted when they contain `;` or newlines). Required columns:
`name` and `promptTemplate`; useful ones: `description`, `showIn`
(`VERSE_SELECTION`, `TEXT_SELECTION`, `WINDOW_MENU`, `WORKSPACE_MENU`, `NOTE_EDITOR`, comma-separated),
`orderNumber`, `bibleOnly`. Give every prompt a fixed `id` (a UUID): it is how a prompt stays the same
prompt across updates. Pack prompts are read-only in the app; users can copy one to edit it. Prompts
from a pack carry no tool permissions of the user's; they ask as usual.

Sample: `docs/addon-samples/prompt-sample/`.

## Bookmark collections

A collection gives the user **read-only labels** (and their bookmarks, notes and StudyPad text) without
anyone building the list by hand: highlighted in the text, listed with the user's own labels, searchable,
openable as a StudyPad, and usable as the Verse widget's source.

### Make a collection

You make it with the app itself; no extra tool exists.

1. In AndBible create the label (or StudyPad), bookmark the verses, add notes and, for a StudyPad, text
   and paragraph breaks. The label's name, colour, style and icon are carried.
   *Starting from a spreadsheet?* Import the CSV (`docs/csv-quick-guide.md`) into the app, so it becomes a
   label, and continue from there.
2. Export the labels as Study Pads (*Study Pads* from the main menu -> *Export Study Pads*, see the
   Study Pads documentation). The result is a `.abdb.zip`; the bookmark database inside it
   (`db/bookmarks...sqlite3`) is the collection file. Several labels can be exported into one file;
   a module then provides all of them.
3. Put the database file in the module's `DataPath` folder (for example `and-bible/manna.sqlite3`) and
   point the conf at it: `AndBibleProvidesLabels=and-bible/manna.sqlite3`.
4. Zip `mods.d/` and the data folder and publish the zip to a repository (below).

Notes:

- Older export formats are migrated when the file is read, so an old export still works.
- Generic (non-Bible) bookmarks are imported if present, but are not a design target.
- Speak, "unlabeled", paragraph-break and AI labels are not imported as collection labels. Links to the
  paragraph-break (and unlabeled) label are kept and use the user's own special label, so a StudyPad's
  paragraph breaks keep working. The author's favourite flags and links to the speak/AI labels are dropped.
- The module directory is never written to. Imported rows live in the user's bookmark database, marked as
  belonging to the module; they never sync (every device builds the collection from its own copy of the
  module) and they disappear with the module.
- **Ids.** Every imported row gets an id derived from (module initials, original id). Re-exporting the same
  label keeps its identity, so an update does not break a Verse widget, a workspace's label visibility or a
  favourite. Two modules that contain the same source label do not share rows.
- Keep one idea per module. A user who removes a module loses its labels and nothing else.

### What users can and cannot do

Collection labels are read-only: no rename, recolour, delete, merge, or adding verses to them; their
bookmarks and notes cannot be edited or removed, and text entries in the StudyPad cannot be edited.
Users **can** favourite the label, hide it per workspace, use it as a Verse widget source and **copy it to
their own labels** (a normal, editable, synced copy). If they add their own label, note or StudyPad to a
collection verse, the app makes their own bookmark for that verse that stays in the collection and stands in
for the original (the verse shows one highlight).

### How updates reach users

On every start, and after a module is installed, updated or removed (also through document sync), the app
compares each collection module's `Version` and a hash of the collection file with what it imported last:

- *new*: the label is imported;
- *changed*: the previous import is replaced in one step (a crash leaves the old content);
- *unchanged*: nothing happens;
- *module removed*: its labels and rows are deleted; the user's own copies stay.

So an update needs a higher `Version` (the hash catches a file changed without a version bump, but do not
rely on it). Because ids derive from the original ids, a verse you keep keeps its identity. A verse you drop
from the collection leaves the user's own copy of it alone.

### Sample

`docs/addon-samples/collection-sample/` is a complete module: two labels (a verse list and a StudyPad with
text and a paragraph break) from John and the Psalms. `docs/addon-samples/build.sh` produces the
installable zips and a repository layout; `python3 docs/addon-samples/make_collection_sample.py` rebuilds the
sample database.

## Publishing

AndBible reads a SWORD repository: a `mods.d.tar.gz` with the conf files and a `packages/` folder with the
module zips (see `website/content/en/docs/custom_repositories.md` for custom repositories, which must be
HTTPS). `docs/addon-samples/build.sh` builds exactly that layout under `docs/addon-samples/repo/` for testing.
The official AndBible repository is published by the maintainers.

## Versions and updates

- Bump `Version` for every release of a module; a higher `Version` is what users see as an update.
- Never reuse a prompt `id`, a label id (i.e. keep re-exporting the same label rather than making a new one
  with the same name) for a different thing.
- Raise `AndBibleMinimumVersion` only when the module needs a feature of a newer app.

## Testing your module

1. Build a repository with the sample script, serve it over HTTPS, add it under *Download documents ->
   Custom repositories* and install the module; or install the zip directly (*Install from zip*).
2. For a collection: open the label list (it shows `Name · Module`), open the StudyPad, check highlights in
   the text, copy the label, then remove the module and check that the label is gone and the copy remains.
