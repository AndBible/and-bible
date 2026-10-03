# Memorize

The Memorize feature helps you learn Bible verses by heart using interactive
exercises.

## Opening the Memorize view

1. Select a Bible passage by long-pressing a word and extending the selection.
2. In the [Verse Action Dialog](verse_action_dialog.md), tap the
    **Memorize** button.
3. A new window opens with the Memorize view.

The Memorize view offers four study modes that you can switch between using
the tabs at the top of the window.

## Study modes

### Word Blur

Words in the passage are progressively hidden. Try to recall the hidden
words from memory. Each tap of the **+** button increases the blur level
(more words are hidden); use the controls to reveal them again as needed.

- **Eye (Reveal)** – temporarily reveal all hidden words while held.
- **Back-step (Reveal last level)** – undo the most recent blur step,
    bringing back the words hidden last.
- **+ (Increase blur level)** – hide more words.
- **Undo (Reset)** – start over with all words visible.

Word Blur does not save progress automatically; it is a free-form
self-test.

### Word Scramble

The words of the passage are scrambled and displayed as buttons below the
text. Select the correct words in order to reconstruct the passage.

- Blanks (`___`) show where the next word should go.
- Hold the **Peek** button to temporarily see the full text.
- Tap **Reset** to start over.

When you finish the passage correctly, the verses are saved as memorized
(see *Saving progress* below).

### Word Type

The passage is shown with words to type. Use your keyboard to enter the
text one word (or one letter) at a time. The behavior depends on the
**Type full words** setting (in Settings > Progress & memorization):

- *Off* (default) – you only need to type the first letter of each word
    to advance.
- *On* – you need to type each word in full.

Other Word Type settings include word visibility (light hint / dim /
hidden) and an error heat-map that highlights words you mistype most
often.

When you complete the passage, the verses are saved as memorized.

### Word Order

The words of the passage are displayed out of order. Drag them into the
correct sequence to reconstruct the passage.

When you complete the passage, the verses are saved as memorized.

## Menu actions

Tap the three-dot menu (top right of the Memorize view) to access:

- **Mark as memorized** / **Marked as memorized** – manually mark the
    selected verses as memorized, or remove the mark.
- **Add memorization target** / **Remove from targets** – toggle the
    selected passage as one of your active memorization targets. Targets are
    passages you are actively working on memorizing.
- **View reading progress** – open the [Reading Progress](reading_progress.md) view.
- **View reading progress settings** – open the **Progress &
    memorization** settings page where Memorize and reading-tracker
    behavior is configured.
- **Listen in loop** – play the passage repeatedly with text-to-speech
    to help with auditory memorization.

## Memorized state

A **green border** is drawn around the Memorize view whenever the
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

    This automatic marking can be turned off (Settings > Progress &
    memorization > *Auto-mark as memorized*) if you prefer to mark verses
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
