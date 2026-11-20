# Labels and highlights {#labels}

## Overview

Labels are a way to tag a piece of information in *AndBible*. Labels can be assigned
to bookmarks, study pads, and personal notes.

## Open Label settings

To manage and see a list of all of your labels:

1. Click the vertical ‘three-dot’ menu on the top right of the main toolbar.
2. Click *Label settings…*. This opens the *Labels* screen.

Alternatively, when creating a bookmark, you can assign and manage labels by
clicking on the *Select or edit labels…* button on the bottom of the
*Bookmark labels* sheet.

[![Labels screen with favourite, auto-assign and main label controls](/media/docs/images/label-settings-dialog.png){ .align-center style="width: 400px; max-width: 100%; height: auto" }](/media/docs/images/label-settings-dialog.png)

## Searching for a Label

If many labels exist, it may be helpful to search for specific labels:

1. [Open Label settings](#open-label-settings)
2. Begin typing the name (or part of the name) of the label you want to find.
    (The results will automatically filter as you type)

## Create a label

To create a label:

1. [Open Label settings](#open-label-settings)
2. Click the *Plus* (+) button at the top
3. Enter a label name.
4. Optionally, [Change Label Settings](#change-label-settings)

## Delete a label

To delete a label:

1. [Open Label settings](#open-label-settings)
2. Click the label you would like to delete.
3. Click the Trash icon in the top right of *Edit label*.
4. *AndBible* asks *Delete the label “…”?* (its Study Pad notes are deleted too). Click *Delete* to confirm. If some bookmarks have no other label, you can choose *Delete label only* (the bookmarks stay without a label) or *Delete label and N bookmarks* (the bookmarks and their notes are deleted).

## Change Label Settings

Click a label in the list to open *Edit label*. Various label settings exist to
apply different styling to the label and make assigning it easier. You can:

> - Change the name, colour and custom marker icon of a label by clicking the row
>     with the colour circle and the name at the top. (The custom icon will be
>     used as the bookmark icon if the label is the main label on the bookmark)
> - Favourite a label with the heart button, to make it easier to select when
>     assigning bookmark labels.
> - Choose the style of text selection bookmarks: *Highlight*, *Underline*,
>     *Marker only* or *Hidden* (no styling or visible icon). Switch on
>     *Use a different style for whole-verse bookmarks* to choose a separate
>     style for them. *Show style examples* in the ⋮ menu of the label list
>     shows what the styles look like.
> - Under *This workspace*, switch on *Auto-assign label to new bookmarks*.
>     (This applies to the active workspace)
> - Set the label as the **Main label** for new bookmarks
>     (*Auto-assign as main label*). The main label is the label that is
>     listed first and determines the style of the bookmark. You can also make a label
>     the main label on a single bookmark, under *This bookmark*.

In the label list the lightning bolt marks auto-assign labels, the heart
favourites and the bookmark icon main labels. On a narrow screen (a phone held
upright), the list you open to choose a bookmark's labels leaves these three icons
out so that the label names have room; tap a label to set them in its settings.
In this narrow list, *Search settings* also leaves out the auto-assign and favourite
filters, since their states are not displayed in the rows. Wider lists, and the
workspace label settings list, keep these filters. If a wider list becomes narrow
(for example when resizing a window), active auto-assign and favourite filters are
cleared so that no invisible filter continues to hide labels.
The ⋮ menu also has *Re-order* for changing the order of the labels.

## Add-on collections {#add-on-collections}

Some add-on modules provide a ready-made **collection**: one or more labels with their
verses, notes and, for a Study Pad, its text. Install one like any other document
(*Download Documents*, or a [custom repository](custom_repositories.md)); the labels
appear at once, highlighted in the text and listed with your own labels as
*Name · Module*, and each has a Study Pad you can open.

Collection labels are **read-only**:

- You cannot rename, recolour, delete or merge them, add verses to them, or edit or
    delete their bookmarks, notes and Study Pad text. *Edit label* shows
    *From add-on …* (tap it to find the add-on in *Download Documents*) instead of the
    editing controls. If you try anyway, *AndBible* tells you the content comes from an
    add-on.
- You can still make a collection label a favourite, choose in *This workspace* how
    it is shown (including hiding it), and use it as the source of the Verse widget.
- To get an editable version, open the label and tap **Copy to my labels**. The copy is
    an ordinary label (named *… (copy)* if the name is taken) with its own bookmarks, notes
    and Study Pad, and it syncs like your other labels. The collection stays as it was.
- You can add your own label, Study Pad or note to a verse of a collection. *AndBible* then
    keeps your own bookmark for that verse in the collection, and the verse shows one highlight.
    If you remove your bookmark or its label, the collection's original verse is shown again.

Collections are never synced as bookmarks: each device builds them from its own copy of the
module. When the module is updated, the collection is replaced with the new version; when you
remove the module, its labels disappear, while your own copies and bookmarks stay. A backup
or Study Pad file that contains collection items does not bring them back as your own; after
such an import *AndBible* tells you how many items were skipped, and *Copy to my labels*
gives you your own copy.

## Override Label Style per Workspace

By default, a label’s display style (*Highlight*, *Underline*, *Marker only*, or *Hidden*)
is the same across all workspaces. You can override the display style for a
specific workspace so that the same label appears differently depending on
which workspace you are in.

For example, you might want a label to show as a colored highlight in your
study workspace but be hidden in your reading workspace.

To set a workspace-specific style override:

1. [Open Label settings](#open-label-settings)
2. Click the label you want to override.
3. Open the **This workspace** section.
4. Under **Override style**, select the desired display mode:

    - **Highlight** — colored background
    - **Underline** — underline style
    - **Marker only** — small marker icon, no highlight or underline
    - **Hidden** — bookmark is not visible

    **No override** uses the label’s global style. Selecting it removes the
    workspace override.

A label that has a workspace-specific override shows a *Workspace* style tag.

## Video

https://www.youtube.com/watch?v=0AwBktLOup8
