# Documents

## Overview

*AndBible* supports various document modules: [Bibles](#bibles), [Commentaries](#commentaries), [Dictionaries](#dictionaries),
[Books](#books), and [Maps](#maps). You can [Download Documents](#download-documents), [Backup and Restore](backup_restore.md)
documents, and [Load Documents](#load-documents) from your device’s filesystem.

## Document Library

The *Document Library* is the list of all Bibles, commentaries, dictionaries,
books, and other modules that have been downloaded. In the app the screen is titled
*Document*. It can be opened in a few different ways:

> - Click the top left main menu (☰).
> - Click *Choose Document*.

Long-pressing the title at the top of the screen, or a toolbar button's *All documents…*
row, also gets you to the document list; see [Opening Documents](navigation.md#opening-documents).

## Bibles

Bibles modules can be installed in different languages, translations, and formats.
Many Bible modules contain Strong’s numbers, footnotes, cross-references, and study notes.

With **Footnotes** enabled in the text options, tap a note marker to read its
contents. Notes at the end of a chapter or book work the same way as other notes;
they are not part of the Bible text. Enable **Footnotes inline** only if you
want the note contents displayed directly in the text.

Error messages that AndBible shows inline in place of missing content (for
example in a view with several references) use the message's own text direction
rather than the module's direction. This keeps messages readable when the
interface and the module use different writing directions.

<span id="id1"></span>

### Cross-References

Cross-references link related Bible passages together, helping you discover
connections across Scripture.

**Built-in cross-references**

Many Bible modules (such as KJV, ESV, and others) already contain cross-reference
data. To display them:

1. Open the text options for your window or workspace (via the popup menu or the
    kebab menu).
2. Enable **Cross references** to show cross-reference indicators in the text.
3. Optionally enable **Inline cross references** (off by default) to display the references inline
    rather than in a popup dialog.

When enabled, cross-references appear as small orange superscript markers in the
Bible text. Tap a marker to open a dialog listing the related passages. If inline
expansion is enabled, the references are shown directly in the text instead.

Choose **Open all references** to view the listed passages together in the links
window. Its title uses localized abbreviated book names (for example,
**Ecc 7:29, Gen 1:26** in English) and lists each reference only once.

**Treasury of Scripture Knowledge (TSK)**

For comprehensive cross-references covering every verse in the Bible, install the
**TSK** (Treasury of Scripture Knowledge) or **TSKe** (extended edition) module:

1. From the top left main menu (☰), click `Download Documents`.
2. Search for **TSK** and install the module.
3. Open TSK in a separate window (see [Windows](windows.md)) or use the links window to
    view cross-references alongside your Bible text.

## Commentaries

Commentaries contain explanations or interpretations of other Bibles or books.
Many commentaries will contain interactive Bible references, and can be
[navigated](navigation.md) like Bible modules.

Some commentaries cover only selected passages. At a passage without a note,
the module may display its own notice instead of commentary. Very long runs of
identical entries are displayed at the selected verse without expanding the
entry's verse range, so these notices do not keep the page loading.
When a commentary window is [synchronized](windows.md) with a Bible, scrolling
updates it to the current verse. If that verse has no commentary entry, a single
not-found notice replaces the previous entry rather than accumulating notices
for earlier verses.

## Dictionaries

Dictionaries provide additional information about individual words. *AndBible* supports
Greek and Hebrew Strong’s dictionaries, Robinson’s Greek morphology, and others.

Strong’s and Robinson dictionaries can be used as the primary lookup for words
in Strong’s enabled document modules (like KJV, BSB, NASB, NET, etc):

1. From the top left main menu (☰) click *Application preferences*
2. In the *Dictionaries* section, click on *Strongs Greek dictionary* to select the Strong’s Greek dictionary you want to use.
3. Click on *Strongs Hebrew dictionary* to select the Strong’s Hebrew dictionary you want to use.
4. Click on *Robinson Greek morphology* to select the Robinson Greek dictionary you want to use.
5. In the toolbar at the top, press the *Strong’s numbers* button (the Greek/Hebrew icon)
    until dotted lines appear below the words.

To view the Strong’s definition for a word, tap the word in a Strong’s enabled Bible
module. Depending on the Strong’s mode and on whether the word has other actions (for
example a bookmark), the definition either opens straight away, or a menu appears where
you choose *Definition & Morphology*. The definition page has a *Find all occurrences*
link, which searches for every verse that uses the same Strong’s number. This also works
in non-English modules such as SpaRV1909; install and select the Greek and Hebrew
dictionaries you want to use.

To display the Strong’s numbers in the Bible instead of underlines,
click on the Greek or Hebrew icon on the top menu bar (For books in Hebrew,
this icon shows Hebrew letters and for books in Greek the icon shows a Greek letter).

Each press of this icon switches the active window between `Hidden Links`, `Links`
(underlines) and `Text and Links` (Strong’s numbers shown in the text). Long-press the icon to
open the *Strong’s numbers* setting for the window.

The dictionary definitions open in a special window rather than a pop-up dialog
when the *Links window* option is on, which it is by default:

> - From the top left main menu (☰) click on *Application preferences* and
>     check the *Links window* option.

### Word lookup dictionaries {#word-lookup-dictionaries}

Other (plain) dictionaries, such as an English dictionary, are used for looking up any word you
select in the text. Select a word and choose *Look up in dictionaries* from the selection menu
(see [Verse Action Dialog](verse_action_dialog.md#text-selection-menu)); the entries that match
are shown together. The menu row is only offered when at least one lookup dictionary is
enabled, and a message tells you if the word is not found.

By default every installed dictionary is used, except the Strong’s and Robinson dictionaries above.
To choose which ones are used:

1. From the top left main menu (☰) click *Application preferences*.
2. In the *Dictionaries* section, click *Word lookup dictionaries* and check the dictionaries you
    want to use for word lookups.

## Books

Various book formats are supported by *AndBible*. Sword modules, EPUB, and
*AndBible* [document backups](backup_restore.md) can all be imported from provided
repositories (Sword) or directly loaded from your device’s filesystem.

Since version 5.0, *AndBible* also supports ePub digital books, which can be loaded from
your device’s filesystem and read as generic books. An EPUB appears in the document
list under the *Book* group. Its table of contents (the list of its chapters) opens from the title at the
top of the screen. EPUBs have their own search modes, including *Raw query syntax*; see
[Searching EPUB books](search.md#searching-epub-books).

The link action menu appears only when a tap has several possible actions; a plain tap
on a link simply follows it. In that menu, links to another location within an EPUB or to
other content inside *AndBible* are labeled **Open internal link**. Links that open
outside the app, such as web or email links, are labeled **Open external link**; on
Android you are asked to confirm before a web link opens in your browser.

There is also experimental support for MyBible, MySword and e-Sword (`.bblx`, `.bbli`)
format modules, which can be loaded from your device’s filesystem.

For newly imported MyBible Bibles, *AndBible* selects the closest supported chapter
and verse numbering from the module's contents and remembers that choice. For example,
German Bibles with Malachi 3:1–24 keep all 24 verses in chapter 3 and do not show a
separate chapter 4.

MyBible modules already installed before this update keep their previous KJVA
numbering so that saved bookmarks, highlights, notes, reading positions, and
reading-plan locations are not reinterpreted. To use detected numbering for an
existing module, delete the module from *AndBible* and import it again after updating;
restarting or restoring the same module does not re-detect its numbering.
Bookmarks made under the old numbering may shift after re-importing, as may other
previously saved locations. Keep a backup before doing so, and be careful when
syncing with older app versions.

Module backups made with this version preserve the selected MyBible numbering when
restored on another device. Older module backups do not contain that information
and restore with the historical KJVA numbering. A backup that names a numbering
system this app version does not recognize also restores with KJVA. Syncing bookmarks alone does not
transfer a module's numbering choice: use the same module backup on both devices
rather than importing the module independently when retaining saved locations.

Bible verse links in MyBible commentaries work like other Scripture references.
When an entry contains two or more references, **Open all** opens them together.
Web links and Strong's dictionary links remain separate from this reference collection.

## Maps

Map modules can be installed through the provided repositories.

## Download Documents

Thousands of different documents can be downloaded from the provided module
repositories.

To download a document from the provided repositories:

1. Click on the top left main menu (☰).
2. Click *Download Documents*.
3. If *AndBible* asks *Do you want to proceed to download documents?*, answer *Yes*
    (*Yes, do not ask again* skips the question in the future, *Cancel* returns).
4. Search for a document and tap it, or tap the download arrow on its row. *AndBible*
    asks *Download* followed by the document name; confirm with *OK*. A progress bar
    with a cross appears on the row while the download runs; the cross cancels it.

The list has two chips at the top: the language chip (the globe) and the document type
chip (*All types*, *Bible*, *Commentary*, *Dictionary*, *Book*, *Map*, *Add-ons*). A line
under them tells how many documents match. The *More filters* button opens *Filter and
sort*:

- *Repository*: show documents from *All repositories* or choose one repository.
- *Sort order*: drag the criteria into priority order, with the most important first.
    The choices are *Status*, *Recommended first*, *Document type*, *Name*, *Language*,
    *Repository* and *Size*. Tap the arrow beside a criterion to switch between
    ascending and descending order. Later criteria break ties in earlier ones.
- *Group by*: choose *No grouping*, *Document type*, *Language* or *Repository*.
    Grouping divides the list into sections; the sort order applies within each section.

Tick *Remember these settings* to keep your choices, or use *Reset to defaults*.
Pull down on the list to refresh the catalogue.

A document that is already installed shows a check mark. When a newer version is in the
catalogue the row shows an upward arrow, and with the default sort the status
*Update available* is listed near the top. Tap the row to update it. If you have bookmarks
or notes for that document, *AndBible* warns you (*There are bookmarks and/or notes for
this document.*) and recommends a backup before you continue.

The language filter starts with your last picked language when it is available in
the catalogue, otherwise your system language. If neither is available, it shows
*All*. This also applies after *Easy Start* and when downloading your first Bible.
Use the language filter to choose another language or *All*; refreshing the
catalogue keeps your current choice.

To view a document’s description and module version history, when provided,
press and hold the document to select it and tap the information (*About*) icon in the
bar that appears. Version history is listed newest first, using
numeric version order (for example, 1.10 comes before 1.9).

Repository and module downloads require HTTPS. A repository must not redirect a
secure download to an insecure HTTP address. If a connection cannot be established
or stops sending data for five seconds, the download times out; a large download
can continue for longer as long as data keeps arriving. If a download fails, check
your connection and try again.

If the app is closed, or the system stops it, before a download has finished, the
document is not installed. The next time you open the app it tells you which
documents were not installed and offers *Download again*. On iOS a download keeps
running for a short while after you switch to another app.

## Load Documents

To load a document from your device’s filesystem:

1. Click on the top left main menu (☰).
2. Click *Choose Document* (or *Download Documents*).
3. Click the three vertical dot menu on the right of the top menu bar.
4. Click *Load Documents From Files*.
5. Click *Proceed* and select a document from your device’s file browser.

*Load Documents From Files* is in the three dot menu of both *Choose Document* and
*Download Documents*. It is not shown in *Download Documents* while you are downloading
your first document; the welcome screen has its own *Load Documents From Files* button.

The dialog lists the file types that can be loaded:

- a zip file containing SWORD module(s), or a documents backup file (`.abmd`) created by
    *AndBible* (see [Backup and Restore](backup_restore.md))
- MyBible documents (`*.SQLite3`)
- MySword documents (`*.mybible`)
- e-Sword documents (`*.bblx`, `*.bbli`)
- EPUB documents (`*.epub`)
- TTF fonts (`*.ttf`)
- AI prompt add-ons (`*.csv`, see [AI](ai.md))
- Study Pads from *AndBible* databases (`*.abdb`)

On iPhone, iPad and Mac, zip, EPUB and font files also open with **Open in**. On Windows and Linux you can pass files on the command line or open them from the file manager, and more formats are accepted (see [Platform differences](platform_differences.md)).

When you select an AndBible documents backup, confirm that you want to restore its
documents. If any files already exist, AndBible also asks before overwriting them.

Replacing a SWORD module through a zip file, document backup (`.abmd.zip`), or
document sync makes the new text available for subsequent reads without restarting
the app. The old search index is removed; rebuild the index to search the new version.
Replacing an EPUB through a document backup also reloads its content and styles
without requiring you to force-stop the app.

If an EPUB replacement fails, AndBible restores the previous version. If a storage
error also prevents that recovery, it keeps the previous version separately and
retries recovery on the next startup. The saved version is not discarded just
because the failed replacement's files are still present.

If AndBible is stopped right after an EPUB replacement succeeded but before it was
finalized, the previous version can come back the next time the app starts.
Replace the EPUB again in that case.

On Android, the installation screen keeps the orientation it had when opened,
including landscape. Rotating the device during installation does not change the
screen orientation or restart the installation. The previous orientation setting
is restored when you leave the installation screen.

## Deleting Modules

To delete any of the above module types:

1. From the top left main menu (☰), click on “Choose Document”.
    (Alternatively, long-press the title at the top of the screen and choose *All documents…*.)
2. Search for the document you want to delete, or filter by the document type.
3. Click and hold on the document you want to delete.
4. Once highlighted, click on the trash can icon in the top menu.
5. Click *Delete* to confirm deletion (the question reads *Delete <name>?*, or *Delete
    these documents?* when several are selected).

Documents can be deleted the same way from *Download Documents*.
