# Platform differences

AndBible 6.0 is the same app on every platform; these are the differences.

The columns are **Android**, **iPhone, iPad & Mac** and **Windows & Linux**. On a Mac, AndBible is the iPad app running on an Apple Silicon Mac, so the iPhone, iPad & Mac column applies to it. These are the main differences.

## Sync {#sync}

See [Cloud synchronization](cloud_sync.md) for how to set sync up.

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Google Drive | Yes | Yes | Yes |
| iCloud Drive | No | Yes | No |
| Nextcloud | Yes | Yes | Yes |
| WebDAV | Yes | Yes | Yes |
| When it syncs | On opening, every 5 minutes while open, on leaving the app, and when the network comes back | On opening, every 5 minutes while open, on leaving the app, and when the network comes back; also in the background when iOS gives the app time | On opening, every 5 minutes while open, and a last time when you quit |
| While it syncs | A "synchronizing" notification | Nothing visible | Nothing visible while open; a short status window when you quit |

Every platform also fetches changes made on your other devices on each of these syncs, not only when you have changed something yourself.

On Windows and Linux, quitting shows a short "synchronizing" window while your changes are uploaded. The sync on quit only sends changes you have not sent yet. **Quit now** skips the wait.

## Reading and display {#reading-and-display}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Speak (text to speech) | Yes | Yes | No |
| Night mode follows the system | Yes | Yes | No, choose light or dark yourself |
| [System colors (Material You)](look_and_feel.md#system-colors) | Android 12 and newer, opt-in | No | No |
| Keep the screen on while reading | Yes | Yes | No |
| Scroll with the volume keys | Yes | No | No |
| Tilt to scroll | Yes | Yes | No |
| Scrollbar in the text | The system scrollbar while scrolling; hidden in Monochrome and Color e-ink | The system scrollbar while scrolling on iPhone and iPad; a thin bar, always shown, on a Mac or with an iPad mouse or trackpad (drag it) | A thin bar, always shown; drag it with the mouse |
| Double-tap to full screen, default | On | On on iPhone and iPad, off on a Mac | Off |
| Home-screen widgets | Yes: [Speak, Listening list and Verse](#speak-widgets) | Yes: the same widgets, plus Lock Screen widgets on iPhone | No |

### Home-screen widgets {#speak-widgets}

AndBible has four home-screen widgets: **Speak**, **Speak with text**, **Listening list** and **Verse**. They follow the
system light or dark mode, and use the Material You colors on Android 12 and newer. In [Monochrome](look_and_feel.md) mode
they are black, white and grey.

**Android.** The standard app (not the discrete calculator app) offers the four widgets. To add one, press and hold an
empty place on the home screen, open the widgets list, find AndBible and drag a widget to the screen. Every widget can be
resized, and shows more the larger it is. Tapping the background of a widget opens AndBible.

| Widget | Size (cells) | Shows and does |
|---|---|---|
| Speak | 2×1 to 5×1 | 2×1: play/pause and the verse being read with its progress. 3×1: also a progress bar, previous and next verse and the sleep timer button. 4×1: also the title and rewind, stop and fast-forward. 5×1: also the status line and the time left on the sleep timer. |
| Speak with text | 3×2 to 5×4 | The title, the verse being spoken and previous, play/pause and next. Wider: the status line. 4×3 or larger: also the next verse, the progress bar, and rewind, fast-forward, stop and the sleep timer. |
| Listening list | 2×2 to 5×5 | Your [Speak bookmarks](speak.md#text-follows-speech-and-bookmarks) as rows with the Bible reference, the Bible, a repeat symbol when the bookmark has a repeat range, and the progress in the book. The row being read is highlighted and shows a pause symbol. Tap a row to start speaking from that bookmark. |
| Verse | 2×2 to 5×4 | One verse, with its source and the date, its reference and Bible, and buttons to speak it or shuffle to another one. Tap the verse to open it in AndBible. |

- When nothing is being spoken, the Speak widgets say "Continue from" and the place where Speak would carry on; play starts from there.
- The sleep timer button turns the [sleep timer](speak.md#sleep-timer) on and off. When switched on, it uses the last time you set, and the widget shows the minutes left.
- Previous and next move one verse.
- The Listening list shows as many rows as fit. Until you have Speak bookmarks it says so and explains how to get them: turn on **Automatic bookmarking** in the Speak settings, or give any bookmark the Speak label.
- The old Small, Middle and Large speak control, Speak text control and Speak bookmarks widgets that you may have placed earlier keep working: they now look like the Speak, Speak with text and Listening list widgets. The widget list shows only the four widgets above on newer Android versions.

#### Verse widget {#verse-widget}

When you add a Verse widget, a window asks what it should show. You can change this later on Android 12 and newer by
long-pressing the widget and choosing to reconfigure it, and on any version by tapping the line with the source and date.

- **Verses from**: **A label** (one of your own labels; the verses are its Bible bookmarks), **Random verse** (**Whole Bible**, **New Testament** or **Psalms & Proverbs**) or **Today's reading plan** (the first passage of today's reading; offered only while you have a reading plan).
- **Changes**: **Daily**, **Every few hours** (every three hours) or **Only when I tap shuffle**. The widget shows the same verse for the whole period, and changes once at its end.
- **Bible**: **Default** (the Bible of the current window) or any installed Bible.
- **Show shuffle and speak buttons** and **Show source and date**.

The speak button speaks the verse the same way the verse menu's Speak does, and replaces what is being spoken. You can
place several Verse widgets with different settings, for example one for each label. If the label of a widget has been
deleted or has no Bible bookmarks, or no Bible is installed, the widget says so, and tapping it opens its settings.

**iPhone and iPad.** The same four widgets are available in the widget gallery, in small, medium and large sizes where they
fit. On the iPhone Lock Screen you can add a round play/pause widget with a progress ring, a rectangular widget with the
reference and progress (or a verse), and a one-line widget with the reference. The Verse widget is set up with
**Edit Widget**. Buttons start speaking without opening the app on iOS 17 and newer; on iOS 16 they open the app.
Open AndBible once after an update to fill the widgets with verses.

## Files and sharing {#files-and-sharing}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Choose a file inside AndBible (install from zip, restore a backup, import) | The system file picker | The system file picker (Files) | The system file picker |
| Open a file from outside AndBible | Zip, EPUB and font (`.ttf`) files from the share sheet or a file manager | The same, with **Open in** | **Open with** AndBible in the file manager; also CSV, images and several Bible database formats |
| `read.andbible.org` links open in the app | Yes | No, they open in the browser | No, they open in the browser |
| Exporting a file (backup, export) | Asks **Share** or **Phone storage** | Asks **Share** or **Save to Files** | Opens the save dialog directly |
| Sharing text | Share sheet | Share sheet | A choice between **Copy** and **Save** |
| Discrete (calculator) mode | A separate discrete app | A setting, with an alternate calculator icon | Not available |
| Import from the previous iOS app | Not applicable | Yes, when old data is found | Not applicable |

On Windows and Linux, opening a file while AndBible is running hands it to the open window instead of starting a second copy. Files can also be passed to `andbible` on the command line.

## Notifications and background work {#notifications-and-background-work}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| System notifications | Android notifications for downloads, sync, indexing and failures | iOS notifications; AndBible asks for permission the first time you start a sync or a download | Desktop notifications on Linux, toast notifications on Windows |

## Install and updates {#install-and-updates}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Where you get AndBible | Google Play, F-Droid and other stores, or a GitHub download | The App Store | GitHub Releases |
| Updates | Through your store | Through the App Store | Optional check on GitHub; opens the release page, and you install the update yourself |
| Requirements | See [Getting started](getting_started.md) | iOS or iPadOS 16 or newer; on a Mac, an Apple Silicon Mac | See [Getting started](getting_started.md#windows) (Windows) and [Getting started](getting_started.md#linux) (Linux) |
