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
| Home-screen widgets | Yes, for [Speak](#speak-widgets) | No | No |

### Speak widgets {#speak-widgets}

On Android, the standard app (not the discrete calculator app) offers five home-screen widgets for [Speak](speak.md). To add one,
press and hold an empty place on the home screen, open the widgets list, find AndBible and drag a widget to the screen. The widgets can be resized.
The widget names below only describe the widgets by size and buttons; the widget picker lists them all as "AndBible" with a preview. Tapping the title or the background of a speak control widget opens AndBible.

| Widget (descriptive name) | Buttons | Shows |
|---|---|---|
| Small speak control | Rewind, play/pause | No title |
| Middle speak control | Rewind, fast-forward, play/pause, stop, sleep timer | Title and the progress in percent |
| Large speak control | The middle widget's buttons plus previous and next | Title and a full status line |
| Speak text control | Previous, next, play/pause, stop | Title and the text being spoken |
| Speak bookmarks | One button per Speak bookmark | - |

- The sleep timer button turns the [sleep timer](speak.md#sleep-timer) on and off; its icon shows which. When switched on, it uses the last time you set.
- Previous and next move one verse.
- The Speak bookmarks widget lists the bookmarks that have the special Speak label, sorted by verse, with the Bible reference and
  the Bible shown on each button, plus a repeat symbol when the bookmark has a repeat range. Tap a button to start speaking from that bookmark.
  Until bookmarks exist it says "Speak bookmarks shortcut buttons will appear here." If **Automatic bookmarking** is off and there are none, it says
  "Autobookmarking disabled!". See [Text follows speech and bookmarks](speak.md#text-follows-speech-and-bookmarks).

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
