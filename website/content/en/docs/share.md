# Sharing

With *AndBible*, you can easily share verses, notes, and study pads.

[![Share verse](/media/docs/images/share-verse.png){ .align-center style="width: 640px; max-width: 100%; height: auto" }](/media/docs/images/share-verse.png)

## Sharing A Verse

On iPhone, iPad and Mac sharing uses the share sheet; on Windows and Linux you choose between **Copy** and **Save**. See [Platform differences](platform_differences.md).

1. Click on the verse you would like to share.
2. In the [verse menu](verse_action_dialog.md), click the *share* button.

    - To share notes in addition to the verse text, click the *share* button
        in the specific note you would like to share. Keep *Show notes* enabled
        in the sharing options. If the bookmark is not tied to a particular Bible,
        the verse text comes from the current Bible.
3. In the *Share selection* sheet, check the preview and toggle the items that you
    want to include in the shared text (see below).
4. Click *Copy* to copy the text to your clipboard, or click *Share* to share
    the text via another app. Both close the sheet.

The sheet has these switches, and the preview changes as you toggle them:

- *Show reference*, *Abbreviate reference*, *Include version name* and *Show at front of verse*
    control how the reference is written and where it goes.
- *Show verse numbers*, *Show quotes* and *Separate verses with newlines* control the text.
- *Show selected text only* and *Show ellipsis (…)* appear when only part of a verse is
    selected; the ellipsis switch works together with *Show selected text only*.
- *Show notes* appears when the bookmark has a note.
- *Advertise app* adds a line telling where the text was shared from.

The switches are remembered for the next time you share.

In the Android text-selection menu (when you select text in the Bible) the corresponding
items are *Share / Copy*, which opens the same sheet, and *Copy*.

[![Share selection options at two scroll positions, with the conditional Show notes switch below](/media/docs/images/share-options.png){ .align-center style="width: 800px; max-width: 100%; height: auto" }](/media/docs/images/share-options.png)

## Copying Footnote Text

To copy text from a footnote popup, long-press the footnote text, adjust the
selection, and choose *Copy* from the text-selection menu. This copies the
selected footnote text without adding the Bible verse or its reference.

## Copying With A Keyboard

With a hardware keyboard, select text and press *Ctrl+C*:

- In a Bible, the selected verses are copied with the options from the
    sharing dialog, such as the reference and the version.
- In a footnote popup, a commentary or any other document, only the
    selected text is copied.

## Sharing A Study Pad

[Study Pads](study_pads.md) can be shared with other *AndBible* users, or with anyone in html
format:

1. Open a Study Pad (See [Study Pads](study_pads.md#opening-a-study-pad) for more details)
2. Long-Click on the study pad window button
3. In the window menu, choose:

    - *Export as HTML* to export an html file that can be shared with anyone.
    - *Export Study Pad* to export a study pad file that can be imported
        by other *AndBible* users.
    - *Export Bookmarks as CSV* to export the bookmarks of the study pad as a CSV
        file (you choose the columns first). See [Bookmarks](bookmarks.md#csv-export-import).
4. When asked *Export to where?*, choose either to save the exported study pad to your
    *Phone Storage* (*Save to Files* on iPhone and iPad) or to *Share* it via another app.

To export several study pads at once, use *Export Study Pads* in the ⋮ menu of
*Study Pads* (main menu) or of the label settings, and tick the study pads you
want. To import a study pad file you have received, use *Import Study Pads* in
the same ⋮ menu. See [Study Pads](study_pads.md#exporting-and-importing-study-pads).

Empty text entries do not include the "Tap to edit text" placeholder in HTML exports.
The placeholder remains visible in the Study Pad so you can add text later.

[![Share studypad](/media/docs/images/share-studypad.png){ .align-center style="width: 640px; max-width: 100%; height: auto" }](/media/docs/images/share-studypad.png)

## Sharing Bibles/Documents {#sharing-bibles-documents}

It is also possible to share other *AndBible* documents. In *Choose Document*, open the three
vertical dot menu and choose *Backup Documents to…*, pick the documents, and then either
*Share* the backup file or save it to *Phone storage*. The file
(`AndBibleModulesBackup.abmd.zip`) can be loaded on another device with *Load Documents From
Files* (see [Load Documents](documents.md#load-documents)). See also
[Backup and Restore](backup_restore.md) for more about backups.

## Sharing Bible Links

You can share a direct link to a specific Bible passage using the
`https://read.andbible.org/` URL format. When another *AndBible* user opens the
link, it opens the passage directly in the app.

For more details on the link format and options, see [Bible Links / Deep Linking](bible_links.md).
