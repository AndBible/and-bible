# Release 6.0 {#release-6-0}

Release Date: **to be announced**

AndBible 6.0 is one app for six kinds of device: Android phones and tablets, iPhone, iPad, Mac,
Windows and Linux. It has a new interface, written once and shared by all of them. Your Bible
text, bookmarks, notes and StudyPads work the same way everywhere.

*This page is a draft and is not final.*

## Where to get it

- **Android:** Google Play, F-Droid, Amazon or Obtainium. See [Android](../getting_started.md#android).
- **iPhone and iPad:** the App Store. See [iPhone and iPad](../getting_started.md#iphone-and-ipad).
- **Mac:** the same App Store listing, on a Mac with Apple Silicon. See [Mac](../getting_started.md#mac).
- **Windows:** an installer from the GitHub releases page. See [Windows](../getting_started.md#windows).
- **Linux:** a `.deb` package or an AppImage from the GitHub releases page. See [Linux](../getting_started.md#linux).

Some features depend on the platform, for example which sync services are available.
See [Platform differences](../platform_differences.md).

## Major new features and improvements

- **Every platform:** AndBible now runs on iPhone, iPad, Mac, Windows and Linux as well as Android.
- **New interface:** all screens are rebuilt with Compose Multiplatform and shared between the platforms.
- **Sync between platforms:** Nextcloud and WebDAV sync work on every platform. iCloud Drive is
    available on iPhone, iPad and Mac, Google Drive on Android.
    See [Cloud synchronization](../cloud_sync.md).
- **Sync now:** start a synchronization yourself from the overflow menu of the reading view.
- **Background activity:** syncing, downloads, search indexing and AI agent runs show in an
    activity list in the menu. See [Navigation](../navigation.md#background-activity).
- **Notifications:** finished or failed background work is reported with a notification on iPhone,
    iPad and Mac, Windows (toast with progress) and Linux. See [Platform differences](../platform_differences.md).
- **Search results:** *Find all occurrences* no longer opens a separate full-screen results page.
- **Links in the text:** on iOS and desktop, AndBible asks before opening an external link you tapped.
- **Reading plans** are included in synchronization.
- **Windows and Linux:** the desktop app uses WebView2 on Windows and Chromium (KCEF) on Linux,
    and installs as an MSI, `.deb` or AppImage. See [Linux and Windows](../desktop.md).

Many more bug fixes, features and improvements.
