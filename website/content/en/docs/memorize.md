# Memorize

The Memorize feature helps you learn Bible verses by heart using interactive
exercises.

## Opening the Memorize view

1. Tap a verse, or select a passage by long-pressing a word and extending the
    selection.
2. In the [Verse Action Dialog](verse_action_dialog.md), tap the
    **Memorize** button near the start of the button row. It keeps its position even when
    the passage is already a memorization goal.
3. The verse menu closes and a new window opens with the Memorize view. When a
    window is maximized, the Memorize view opens in that window instead.

Pressing **Back** in the Memorize view first closes an open menu or gear popup. In a
maximized window that shows Memorize, Back then restores the other windows and keeps the
Memorize view open; only a further Back returns to what the window showed before.

Opening the Memorize view on a passage also adds it as a memorization goal (if it is not one already),
since tapping **Memorize** means you want to learn it. To drop the goal, choose **Remove from Goals** in the
view's menu (see [Menu actions](#menu-actions)); the verse menu also offers **Goal -** for a passage that
is already a goal.

The Memorize view offers four study modes that you can switch between using
the tabs at the top of the window: **Blur**, **Scramble**, **Type** and **Order**.
The sections below call them Word Blur, Word Scramble, Word Type and Word Order.

## Study modes

### Word Blur

Words in the passage are progressively hidden. Try to recall the hidden
words from memory. The buttons above the text have icons but no text labels:

- **Eye** – hold it to reveal all hidden words temporarily.
- **Back-step** – hold it to reveal only the words hidden at the latest blur
    level temporarily. It does not undo a blur step.
- **+** – increase the blur level (more words are hidden, up to level 5). A small
    badge shows the current level.
- **Undo arrow** – start over with all words visible.

You can also tap a single hidden word to reveal it for a couple of seconds.

Word Blur does not save progress automatically; it is a free-form
self-test.

### Word Scramble

The words of the passage are scrambled and displayed as buttons below the
text. Select the correct words in order to reconstruct the passage.

- Blanks (`___`) show where the next word should go.
- A word that occurs several times in the passage has a single button with a count,
    for example `word (2)`.
- Hold the **eye** button to temporarily see the full text.
- Tap the **round arrow** button to start over.
- The gear button opens an option, **Hide used word buttons**, that removes the
    buttons of words you have already placed.

When you finish the passage correctly, the verses are saved as memorized
(see *Saving progress* below).

### Word Type

The passage is shown with words to type. Use your keyboard to enter the
text one word (or one letter) at a time. The behavior depends on the
**Type full words** setting (in `Application preferences` > `Features` > `Progress & memorization`, or in the gear
button of the **Type** tab):

- *Off* (default) – you only need to type the first letter of each word
    to advance.
- *On* – you need to type each word in full.

Punctuation, including curly quotation marks (“ ” ‘ ’), paragraph markers (¶)
and editorial signs such as * and /, is skipped automatically in both typing
modes and is never offered as a word in Scramble or Order. Start with the
first letter of the word, even when a verse begins with one of these markers.

Other Word Type settings, also in the gear button, include **Word visibility** (**Light** /
**Dim** / **Hidden**) and an **Error heatmap** that highlights words you mistype most
often. Tap *Tap here to start typing* to bring up the keyboard.

The setting **Include verse reference** (in `Progress & memorization`) adds the verse
reference as the last item to memorize in every mode.

When you complete the passage, the verses are saved as memorized.

### Word Order

The words of the passage are displayed out of order. Drag them into the
correct sequence to reconstruct the passage. A tile that is in the right place is marked
while you drag. Hold the **eye** button to see the correct text, or tap the **round arrow** to
shuffle again.

When you complete the passage, the verses are saved as memorized.

## Menu actions

Tap the three-dot menu (top right of the Memorize view) to access:

- **Mark as memorized** / **Memorized** – manually mark the
    selected verses as memorized (this also makes the passage a goal, if it is not one yet),
    or, when the row reads **Memorized**, remove the mark.
- **Goal +** / **Remove from Goals** – toggle the
    selected passage as one of your memorization goals. Goals are
    passages you are actively working on memorizing. **Remove from Goals** is offered only
    while the passage is a goal that is not yet memorized.
- **Read/Memory Progress** – open the [Reading Progress](reading_progress.md) view.
- **Read/Memory Settings** – open the **Progress &
    memorization** settings page where Memorize and reading-tracker
    behavior is configured.
- **Listen in Loop** – play the passage repeatedly with text-to-speech
    to help with auditory memorization. It is shown only on platforms that support Speak
    (not on Windows and Linux).
- **View help** – open a short help dialog about Memorize.

## Memorized state

A **green border** (grey in monochrome mode) is drawn around the Memorize view whenever the
selected verses are already marked as memorized in your
[Reading Progress](reading_progress.md). The border is a global
indicator – it is not limited to a particular study mode, and it remains
visible whenever you revisit a passage that has been marked as memorized.

## Saving progress

There are two kinds of progress in the Memorize view:

**Mode state inside the window**
:   The currently selected tab, the active blur or scramble configuration,
    and the per-mode state are saved with the window. As long as the window
    stays open (even minimized), you can switch back and continue where you
    left off. Closing the window discards this in-progress state.

**Permanent “memorized” marking**
:   When you successfully complete an exercise in **Word Scramble**,
    **Word Type** or **Word Order**, the verses are also recorded as
    memorized in your [Reading Progress](reading_progress.md). This is
    reflected in the progress indicators on the book and chapter grids,
    and shown as the green border the next time you view the passage.

    This automatic marking can be turned off (`Application preferences` > `Features` >
    `Progress & memorization` > *Auto-mark as memorized*) if you prefer to mark verses
    manually using the menu action above.

    Word Blur does not produce an automatic marking, because it has no
    well-defined “completed” state.

!!! tip

    To quickly find passages you want to memorize, create a
    [bookmark](bookmarks.md) with a dedicated [label](labels.md)
    (e.g. “Memorize”). You can then access all your memorization passages
    from the Bookmarks view.

## Cloud Sync

Your memorized verses can be synchronized across devices using [cloud sync](cloud_sync.md). Enable the **Reading Progress** toggle in the cloud sync settings
(the same toggle also covers your chapter [reading records](reading_progress.md)).
