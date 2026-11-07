# Platform differences

AndBible 6.0 is the same app on every platform; these are the differences.

The columns are **Android**, **iPhone, iPad & Mac** and **Windows & Linux**. On a Mac, AndBible is the iPad app running on an Apple Silicon Mac, so the iPhone, iPad & Mac column applies to it. These are the main differences.

## Sync {#sync}

See [Cloud synchronization](cloud_sync.md) for how to set sync up.

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Google Drive | Yes (not in the F-Droid build) | No | No |
| iCloud Drive | No | Yes | No |
| Nextcloud | Yes | Yes | Yes |
| WebDAV | Yes | Yes | Yes |
| When it syncs | In the background, with a sync notification | In the background, when iOS gives the app time | Regularly while AndBible is open, and a last time when you quit |

On Windows and Linux, quitting shows a short "synchronizing" window while your changes are uploaded. **Quit now** skips the wait.

## Reading and display {#reading-and-display}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Speak (text to speech) | Yes | Yes | No |
| Night mode follows the system | Yes | Yes | No, choose light or dark yourself |
| Keep the screen on while reading | Yes | Yes | No |
| Scroll with the volume keys | Yes | No | No |
| Tilt to scroll | Yes | Yes | No |
| Double-tap to full screen, default | On | On on iPhone and iPad, off on a Mac | Off |
| Home-screen widgets | Yes | No | No |

## Files and sharing {#files-and-sharing}

| | Android | iPhone, iPad & Mac | Windows & Linux |
|---|---|---|---|
| Open files in AndBible | Zip, EPUB and font (`.ttf`) files from the share sheet or a file manager | The same, with **Open in** | Pass files on the command line or open them from the file manager; also CSV, images and several Bible database formats |
| `read.andbible.org` links open in the app | Yes | No, they open in the browser | No, they open in the browser |
| Exporting a file (backup, export) | Asks **Share** or **Phone storage** | Asks **Share** or **Save to Files** | Opens the save dialog directly |
| Sharing text | Share sheet | Share sheet | A choice between **Copy** and **Save** |
| Discrete (calculator) mode | A separate discrete app | A setting, with an alternate calculator icon | Not available |
| Import from the previous iOS app | Not applicable | Yes, when old data is found | Not applicable |

On Windows and Linux, starting AndBible again while it is running hands the files to the open window instead of starting a second copy.

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
