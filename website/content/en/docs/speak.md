# Speak: listen to the Bible {#speak}

## Voice used for speaking

*AndBible* uses the [Text to Speech](https://www.lifewire.com/use-google-text-to-speech-on-android-4767200) functionality of your device
to speak the text of the Bible, commentaries and books. The quality and listenability of these voices varies greatly and is not something we control.
Most devices allow you to choose a different voice and modify pitch and speed. You can also install 3rd party Text-to-Speech engines which may
be superior to the default voices.

Bible speech preserves words split by formatting or word markup, including in spoken titles.
For example, `LORD` and French `l'Éternel` are passed to the speech engine without extra spaces
inside the word. Spaces present in the source text are kept.

The way in which you change the voice differs between devices. Try searching in the system Settings for `Text-to-Speech > Preferred engine > Install voice data`. The Speak settings also have **Open system TTS settings**, which takes you there.

Speak is available on Android, iPhone, iPad and Mac, but not on Windows and Linux; see [Platform differences](platform_differences.md).

## No sound when speaking

If *AndBible* says that text-to-speech is not working, or that the language is not available,
the problem is in your device's speech setup. Tap `Open settings` in that message to go to the
system text-to-speech settings, then:

1. Check which engine is selected as the `Preferred engine`, and play its example sound. If you
   hear nothing there either, the engine itself is not working.
2. If no engine is listed, or the selected one does not work, install or enable one: for example
   the engine that came with your phone (such as Samsung text-to-speech), Google's
   [Speech Recognition & Synthesis](https://play.google.com/store/apps/details?id=com.google.android.tts),
   or a free engine from F-Droid such as RHVoice or eSpeak NG. An engine that has been
   disabled can be enabled again in the system's app settings.
3. In the engine's settings, download the voice data for the languages you want to hear.

If *AndBible* says that another app is using the audio, stop the other app's playback (or end the
call) and try again.

If the speech engine reports an error during playback, AndBible pauses and shows an error
message instead of silently skipping the failed part. Press Play to try again from the
interrupted verse or passage. If the error keeps returning, check the engine and voice data
using the steps above, or try another speech engine.

## Deleting a document during speech

Deleting the document being read aloud stops speech and clears any paused playback
of that document. This also applies while the speech engine is starting. AndBible
does not automatically switch to another document; choose an installed document
to start speaking again. Deleting a different document does not interrupt speech.

## Speaking commentaries

When a commentary has no entry for a verse, speech skips that verse and continues
at the next verse with text. Empty verses are not announced, even when
**Chapter Changes** is enabled.

Bible passage links in Bible text, commentaries and books are spoken using their full
reference, including the book name, chapter and verse, when the module supplies a valid
Bible link target. For example, a link labelled `v. 9` that points to Matthew 2:9 is
spoken as `Matthew 2:9`. In Bible text, cross-reference notes are never spoken, and
links inside study notes are only spoken when those footnotes are enabled. If a link has no valid Bible target, its visible text is used.

## Speak Settings

To start speaking, choose `☰ > Speak` or tap the Speak button in the toolbar. A playback bar appears at the bottom of the
screen, with bookmark (when a Speak bookmark exists), rewind, previous, stop, play/pause, next and fast-forward buttons.
The settings open from the gear button at the right end of the playback bar, or by long-pressing the Speak button in the toolbar.

The first page, **Playback Settings**, has:

| Setting | |
| --- | --- |
| **Speak speed** | The speed that the text will be read (0 to 300 %). |
| *Chapter changes* | Plays a short sound when a new chapter is reached. |
| *Titles* | Read the titles that translators have added to the Bible text. |
| *Footnotes* | Read the footnotes included in the Bible text. |
| **Set repeat passage range** (under **Repeat passage**) | Repeat the range of verses specified; see [How to speak a range of verses](#how-to-speak-a-range-of-verses). |
| **Set sleep timer** (under **Sleep timer**) | Stop the playback after the time specified; see [Sleep Timer](#sleep-timer). |

Under **Other** there are **Advanced settings**, **Open system TTS settings** and **Help**.

### Text follows speech and bookmarks

**Advanced settings** (under **Other**) has the following switches, in two groups:

- **General Speak Settings**: **Text follows speech** and **Replace divine name with Yahweh**. The latter makes the
  speech engine say "Yahweh" in place of the divine name that Bibles print as LORD (and its equivalents in other languages) where the
  module marks the divine name.
- **Bookmarking Settings**: **Automatic bookmarking** and **Save playback settings to bookmarks**. With the latter on, the
  playback settings (speed, chapter changes, titles, footnotes and repeat range) are saved to each Speak bookmark and loaded
  again when you resume from that bookmark. You can then, for example, listen to Genesis quickly and to Romans slowly and with chapter-change sounds.

**Text follows speech** controls whether the reading
window moves with playback. Changing it does not require restarting the app. With it
disabled, speaking a selected commentary passage keeps the window at its current position
while highlighting the spoken text in the displayed entry, including entries that cover
several verses. Verses that the spoken translation omits (for example Mark 7:16 in some
translations) are skipped, and the reading position follows the next verse that is actually
spoken.

To return to the current speech position after scrolling elsewhere, open the window
menu and choose **Open** with the headset icon (the menu item is shown while speech is not stopped). Its reference shows the current spoken
position, even when **Text follows speech** is disabled. For books such as EPUBs, this
also returns to the spoken paragraph within the chapter.

With **Automatic bookmarking** enabled, pausing or stopping speech saves your position
with the special Speak label. The playback bar's bookmark button appears when a Speak
bookmark is available, without restarting the app, and lets you resume from a saved position.
It disappears when the last Speak bookmark is removed.
In a commentary, the Speak bookmark's marker appears in the commentary entry, including
entries covering several Bible verses. Use **Speak from bookmark** (the bookmark button in the playback bar) to resume from a saved position.

The Speak bookmarks can also be reached from the Android [home-screen widgets](platform_differences.md#speak-widgets).

## How to speak a range of verses

1. In the Speak settings, under **Repeat passage**, tap **Set repeat passage range**.
2. Tap **Choose beginning of passage** and choose the book, chapter and verse at which to start.
3. Tap **Choose ending of passage** and choose the book, chapter and verse at which to end. The ending verse must be later than the starting verse.
4. Confirm with **OK**. The range is now shown under **Set repeat passage range**; the trash button clears it.
5. Push the play `▶` button in the playback bar to start playback.

[![Speak Settings](/media/docs/images/speak-page.jpg){ .align-center style="width: 400px; max-width: 100%; height: auto" }](/media/docs/images/speak-page.jpg)

## Earcon Sounds

Earcon sounds are short audio cues that play during speech. Only one of the switches is an earcon:

- **Chapter changes** – a sound plays when a new chapter begins.

**Titles** and **Footnotes** are not sounds: they decide whether the titles that translators added and the footnotes
included in the Bible text are read aloud.

## Sleep Timer

The sleep timer automatically stops playback after the specified number of
minutes. This is useful when listening to the Bible before falling asleep. Tap
**Set sleep timer** in the Speak settings and choose `Off`, one of the ready-made times (5, 10, 15, 30, 45 or 60 minutes)
or `Custom…`, which shows a slider for 1 to 120 minutes.

## Bluetooth Media Buttons

*AndBible* can respond to Bluetooth media buttons (play/pause on headsets,
car audio systems, etc.) to start and stop text-to-speech playback.

This feature is enabled by default. To disable it, go to `Application preferences` >
`Advanced settings` > `Enable Bluetooth media buttons`.
