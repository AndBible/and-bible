# Bookmarks

## Overview

A bookmark is a [label](labels.md) attached to a selection of text, a verse,
or a range of verses.

When a bookmark is added to an entire verse (or range of verses), the bookmark will
be visible in all other translations. Bookmarks applied to only a selection of text
will, however, only be visible as an icon in other translations.

Multiple [labels](labels.md) can be added to a single bookmark and multiple bookmarks
can be added to a single verse. The verse action dialog shows all bookmarks for
that verse, not only the selected bookmark.

https://www.youtube.com/watch?v=PXdvFRLGcAA

## Creating a bookmark

To create a bookmark:

1. Click on the verse you want to bookmark.
2. In the verse action dialog, click the *Bookmark* button. The *Bookmark labels*
    sheet opens.
3. Optionally, add [labels](labels.md)
    to your bookmark.

To add a bookmark to part of a verse:

1. Click and hold on a word that you want to bookmark.
2. Extend the text selection as required.
3. Choose *Selection* or *Bookmark > Selection* (the options displayed are
    controlled by the ‘One-step bookmarking’ application preference).
    Choosing *Verses* bookmarks the whole verse.
4. Optionally, add [labels](labels.md)
    to the bookmark.

To add a bookmark to multiple verses:

1. Click the first verse you want to bookmark.
2. Click the *Plus* (+) button in the upper right of the verse action dialog.
    The following verse is automatically selected.
3. Click on a later verse to extend the verse range. To shorten the range,
    Click any verse in the existing range and the range will end at that verse.
4. Click the *Bookmark* button and optionally select a [label](labels.md).

## The bookmark card

The verse action dialog shows each bookmark of the selected verse as a card.
The buttons on the card are:

- **Info** (circled *i*): shows the bookmark's details, such as when it was created and last updated.
- **Pencil**: add or edit the bookmark's [note](notes.md).
- **Share**: [share](share.md) the verse (and optionally the note).
- **Whole verse** toggle: marks the bookmark as covering the whole verse, or
    only the selected text. Some bookmarks cannot be turned back into a
    selection bookmark; the app tells you if so.
- **Icons**: opens *Configure bookmark settings* (see below).
- **Study Pad**: there is one Study Pad button per label; each opens that label's [Study Pad](study_pads.md).
- **Trash can**: deletes the bookmark.

https://www.youtube.com/watch?v=rcQv6YNeMpQ

## Deleting a bookmark

To delete a bookmark:

1. Click the verse containing the bookmark you want to delete.
2. The verse action dialog shows all bookmarks for the selected verse,
    not just the bookmark clicked on.
3. Click the trash can icon inside the bookmark you wish to delete.
4. Click *Yes* to confirm deletion.

## Changing a bookmark’s icon {#changing-a-bookmark-s-icon}

To change a bookmark’s icon:

1. Click the verse containing the bookmark you want to change.
2. Click the ‘icons’ button inside the bookmark you want to change. This opens
    *Configure bookmark settings*.
3. On the *Custom icon* tab, choose a new icon for your bookmark. This icon will be displayed at the end
    of the bookmarked verse (or text), before the space separating it from the next verse.

## Bookmarks list {#bookmarks-list}

To see all your bookmarks in one place, open the main menu (☰) and click
*Bookmarks*.

- **Label filter:** the chip under the title shows the label whose bookmarks are
    listed. Click it to choose another label.
- **Sort:** the arrow button in the top bar switches between *Biblical order*
    (ascending or descending) and *Date order*.
- **Labels:** the label button opens the [label](labels.md) manager.
- **Show notes:** in the ⋮ menu, shows the note under each bookmark. While it is
    on, a search button appears that searches the notes.
- **Open a bookmark:** click a row to go to its verse. Long ones can be
    expanded with the arrow on the right.
- **Select several:** long-press a row, then click more rows. The top bar then
    offers *Assign labels* and *Delete* for all selected bookmarks. Deleting asks
    for confirmation.

### Exporting and importing bookmarks as CSV {#csv-export-import}

The ⋮ menu of the bookmarks list has *Export CSV* and *Import CSV*, for
spreadsheets or for moving bookmarks between apps.

- **Export CSV** exports the bookmarks currently listed (the chosen label,
    and the notes search if used). First you choose the columns to include in
    *Select CSV Columns*, then where to save the file
    (`bible_bookmarks_<date>.csv`). The app confirms with *Exported N bookmarks to CSV*.
- **Import CSV** asks for a CSV file and creates or updates bookmarks from it.
    The result is shown as *Import completed: N created, M updated*; if some rows
    failed, the first error messages are listed. The easiest way to get a
    suitable file is to export one first.

A Study Pad can also export its bookmarks as CSV: see [Sharing](share.md#sharing-a-study-pad).

## Experimental features

Experimental features are switched on under Application preferences > Advanced
settings > Experimental features. They may change or be removed.

- **Bookmark edit actions:** see below.
- **Add paragraph break bookmark:** adds a *Paragraph break* button to the verse
    action dialog, and an item to the text selection menu. It creates a bookmark with a special paragraph-break label
    for the selected verse or verses.
- **Use the literal workspace colour for the reading toolbar (instead of the theme colour):**
    the toolbar uses the workspace's own colour as it is, instead of a colour
    adjusted to the theme.

### Edit actions

With **Bookmark edit actions** enabled, the bookmark settings dialog includes an **Edit
action** tab. Choose **Prepend text** or **Append text** to insert content before
or after the bookmarked text. The formatting buttons can insert a paragraph break
or a subtitle.

An insertion mode requires content; empty or whitespace-only content cannot be
saved. Choose **None** to remove the edit action. Previously saved empty edit
actions are ignored when displaying the chapter, without hiding other bookmarks.

## Labels

To learn more about adding labels to your bookmarks, see [Labels](labels.md)
