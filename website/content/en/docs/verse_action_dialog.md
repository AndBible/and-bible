# Verse Action Dialog

Tap a verse to select it and open the verse action dialog. The selection highlight
covers the whole verse, including text in bulleted lists.

The dialog has these buttons. When there is not enough width for all of them, the last ones move
under a **More** button:

- [Bookmark](#bookmark)
- [Note](#note)
- [My Notes](#my-notes)
- [Share](#share)
- [Compare](#comparing-verses)
- [Speak](#speak)
- [Memorize](#memorize)
- **Goal -**, shown when all the selected verses are memorization goals: removes them from the
    goals. See [Memorize](memorize.md).
- **AI**, shown when an AI provider is set up. See [AI](ai.md).
- **Paragraph**, shown only when the experimental *Add paragraph break bookmark* feature is on
    (see [Bookmarks](bookmarks.md#experimental-features)).

At the top of the dialog there is a **+** button that adds the following verse to the selection
(see [Adding a bookmark](bookmarks.md#creating-a-bookmark)), a button that expands or collapses
the dialog, and a **?** button that opens the *Bookmarks & My Notes* help.

With a hardware keyboard, **B** adds a bookmark, **N** adds a note, **Space** speaks the
selected verse, **+** and **−** extend or shrink the selected range, and **Ctrl+C** copies the verses.

## Bookmark

The bookmark icon creates a new bookmark and opens the *Bookmark labels* sheet. If the workspace has
[auto-assign labels](labels.md#change-label-settings), the bookmark is created with those labels instead and the
sheet does not open.
See [Adding a bookmark](bookmarks.md#creating-a-bookmark) for more information.

## Note

The note icon creates a new bookmark and opens its note for editing. The bookmark has the ‘Unlabeled’ label, or
the workspace’s auto-assign labels if there are any.
You can immediately start entering your note or change the label from the *Bookmark labels* sheet.
See [Creating a note](notes.md#creating-notes) for more information.

## My Notes

The My Notes icon displays all the notes you have taken for the current chapter in the Links window.

## Share

The Share icon allows you to copy or share the highlighted verses. See [Sharing Verses](share.md) for more information.

## Speak

The Speak button reads the selected verses aloud. See [Speak](speak.md).

## Memorize

The Memorize button opens an interactive view for learning the selected verses by heart.
It is available only for Bible documents, not commentaries or dictionaries.
See [Memorize](memorize.md) for more information.

## Comparing verses

1. Click on the verse you want to compare.
2. Click the *Compare* button.
3. Hide translations by clicking the *hidden eye* button.
4. Show translations by clicking the translation title at the bottom of the list.

https://www.youtube.com/watch?v=wFG-vlSow6E

## Text selection menu {#text-selection-menu}

Pressing and holding on text starts a text selection and opens the selection menu. On Android
it appears as the action bar at the top of the screen; on the other platforms it is a menu
next to the selection. Which entries it shows depends on what you selected:

- **Bookmark**: bookmarks the selection. In a Bible this opens a second step with *Selection*
    (the selected text) and *Verses* (the whole verses). Turn on *One-step bookmarking* in
    *Application preferences* to show *Selection* and *Verses* directly. See
    [Bookmarks](bookmarks.md#creating-a-bookmark).
- **Compare**, **Memorize** and **Share / Copy**: as in the verse action dialog, for Bible verses.
- **Copy**: copies the selected text. It is shown when the selection is not Bible verses, or is
    plain text.
- **Find "text"** (or **Find** for a long selection): searches the Bible for the selected text as
    a phrase. See [Search](search.md).
- **Open** followed by the reference: shown instead of *Find* when the selected text is a Bible reference such as
    *John 3:16*, and opens that passage.
- **Web search**: searches the web for the selected text in your browser.
- **Look up in dictionaries**: looks up the selected word in your word lookup dictionaries. Shown
    only when at least one is enabled, see [Word lookup dictionaries](documents.md#word-lookup-dictionaries).
- **Paragraph break**: adds a paragraph break at the selected verse; shown only when the
    experimental *Add paragraph break bookmark* feature is on. The same feature adds an item to the text selection menu.
- **AI Actions**: shown when an AI provider is set up. See [AI](ai.md).
