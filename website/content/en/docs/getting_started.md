# Getting Started

## Installation

AndBible runs on Android, iPhone, iPad, Mac, Windows and Linux. Pick your platform below.
What differs between them is listed in [Platform differences](platform_differences.md).

<!-- if beta -->
!!! note "AndBible 6.0 is in public beta"
    The new AndBible 6.0, the first version for every platform, is in public beta. The stores
    below keep the current stable version; each platform section says how to try the beta.
    See [Release 6.0](releases/release_6_0.md) for what is new, and report problems as described
    in [Support](support.md#reporting-issues).

<!-- end -->

### Android {#android}

You can install *AndBible* on an Android phone or tablet using the
[Google Play Store](https://play.google.com/store/apps/details?id=net.bible.android.activity),
[F-Droid](https://f-droid.org/packages/net.bible.android.activity/),
the [Amazon App Store](http://www.amazon.com/Martin-Denham-And-Bible/dp/B004Z2KKYK),
or directly from the [Releases section of our GitHub](https://github.com/AndBible/and-bible/releases/latest)
source code repository.

<div class="ab-badges" markdown>

[![Google play badge](images/google-play-badge.png)](https://play.google.com/store/apps/details?id=net.bible.android.activity)

[![F droid badge](images/f-droid-badge.png)](https://f-droid.org/packages/net.bible.android.activity/)

[![Amazon badge](images/amazon-badge.png)](http://www.amazon.com/Martin-Denham-And-Bible/dp/B004Z2KKYK)

[![Obtainium badge](images/obtainium-badge.png)](https://obtainium.imranr.dev/)

</div>

Note: An apk downloaded directly from the “Assets” sub-menu of the Github Releases
will not automatically update. You can use a tool like [Obtainium](https://obtainium.imranr.dev/)
to download (and update) the apk from Github. AFTER installing Obtainium, you can click
[this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22net.bible.android.activity%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FAndBible%2Fand-bible%22%2C%22author%22%3A%22AndBible%22%2C%22name%22%3A%22Bible%20Study%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22andbible-production%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3Anull%7D)
to load the AndBible config directly into Obtainium.

<!-- if beta -->
**Try the 6.0 beta on Android:** join the
[beta programme on Google Play]({{ play_beta }}) with the Google account of your device, and
Google Play updates AndBible to the beta. You can leave the programme on the same page; you then
go back to the stable version at its next update. Alternatively, download the
`andbible-<tag>.apk` file (not `calculator-…`) from the newest *Beta release* on the
[GitHub releases page]({{ beta_release }}).
<!-- end -->

### iPhone and iPad {#iphone-and-ipad}

Install *AndBible* from the
[App Store](https://apps.apple.com/us/app/andbible-bible-study/id6774904527).
It needs iOS 16 or newer (iPadOS 16 or newer on an iPad).

<!-- if testflight -->
**Try the 6.0 beta:** install Apple's *TestFlight* app, then open the
[AndBible public beta]({{ testflight }}) link on your iPhone or iPad and accept the invitation.
The beta replaces the App Store version on that device; your data stays.
<!-- end -->
<!-- if beta -->
<!-- if not testflight -->
**AndBible 6.0 for iPhone and iPad is coming soon** as a public beta through Apple's
TestFlight. Until then the App Store has the current version of AndBible for iOS.
<!-- end -->
<!-- end -->

### Mac {#mac}

On a Mac, install the same
[App Store](https://apps.apple.com/us/app/andbible-bible-study/id6774904527) listing.
It is the iPad app running on a Mac with Apple Silicon (M1 or newer). There is no native macOS
build, and Intel Macs are not supported.

<!-- if testflight -->
The [TestFlight beta]({{ testflight }}) also runs on an Apple Silicon Mac: install TestFlight from
the Mac App Store and open the link there.
<!-- end -->

### Windows {#windows}

<!-- if beta -->
AndBible for Windows is new in 6.0 and is in public beta.

1. Download the `.msi` file (`andbible-desktop-<version>-x64.msi`) from the newest *Beta release*
    on the [GitHub releases page]({{ beta_release }}).
    It is for 64-bit Windows (x64).
<!-- else -->
1. Download the `.msi` file (`andbible-desktop-<version>-x64.msi`) from the
    [latest release](https://github.com/AndBible/and-bible/releases/latest).
    It is for 64-bit Windows (x64).
<!-- end -->
2. Open it. The installer is not signed yet, so Windows SmartScreen may say
    "Windows protected your PC". Choose **More info**, then **Run anyway**.

See [AndBible on Windows and Linux](desktop.md) for updates and data locations.

### Linux {#linux}

<!-- if beta -->
AndBible for Linux is new in 6.0 and is in public beta. Download one of these from the newest
*Beta release* on the [GitHub releases page]({{ beta_release }}). Both are for 64-bit
Linux (amd64 / x86_64).
<!-- else -->
Download one of these from the
[latest release](https://github.com/AndBible/and-bible/releases/latest). Both are for 64-bit
Linux (amd64 / x86_64).
<!-- end -->

- The `.deb` package (`andbible-desktop-<version>-amd64.deb`): install it with your package manager.
- The AppImage (`andbible-desktop-<version>-x86_64.AppImage`): make it executable with
    `chmod +x andbible-desktop-*.AppImage`, then run it. If it does not start, install FUSE 2
    (`libfuse2`) first.

See [AndBible on Windows and Linux](desktop.md) for details.

## First start

When you open *AndBible* for the first time, your library is empty, and the welcome screen offers
ways to add documents.

If your device is set to English, the screen has two tabs:

- **Easy** downloads a recommended set of Bibles, dictionaries and cross-references with one tap
  (**Download and start**). If you have used *AndBible* on this device before, a
  **Redownload** link lets you pick documents you had installed.
- **Advanced** lists every option, described below.

In other languages there are no tabs and the screen shows the Advanced options directly. The Advanced options are:

- **Redownload Documents**: choose from documents installed earlier on this device (shown only
  when there are any).
- **Download Documents**: browse the online catalog of Bibles, commentaries, dictionaries and
  books in many languages. See [Documents](documents.md).
- **Load Documents From Files**: install from a file. Supported formats are AndBible zip,
  MyBible, MySword and EPUB.
- **Restore Database File**: restore a backup of your bookmarks, notes and settings. See
  [Backup and restore](backup_restore.md).

## Sharing on Android

**Recommend to a friend** opens the system share sheet with a description of AndBible,
a link to the standard app's Play Store listing, and the homepage for other installation
options. The Play link always points to the published standard app, even when your installed
build uses a different package name (for example, Accrescent or a debug build). Samsung builds
omit the Play link. **Rate the app**, when offered, also opens the standard Play listing,
or Samsung Apps for Samsung builds.

**Recommend to a friend** is hidden in discrete mode. Recommendation and rating actions
are disabled while discrete mode is active, to avoid revealing the app's identity.

## Help and sharing on iOS

The iOS drawer's **Help & tips** shows all eight help topics, tutorial and manual links,
and the app version. It does not include the Android development-shop promotion.
**Licence** displays the complete licence bundled with the app, so it is available offline.
**Recommend to a friend** opens the system share sheet with a description of AndBible and
its homepage, without an app-store link.

The iOS **Report a bug** action asks for confirmation before preparing a report with device
and version details, any retained crash details, and a compressed recent app log. If iOS has
delivered a crash diagnostic from the last 24 hours, it is also attached as
`recent_crash_diagnostic.json`. Diagnostics are not sent automatically. JavaScript error
reports use the same consent flow and include the WebView error diagnostic.

The iOS drawer does not currently offer a rating or development-purchase action.

## Help and bug reports on Windows and Linux {#help-and-bug-reports-on-windows-and-linux}

**Report a bug** (*Feedback / bug report* in the navigation menu) asks where to save a report
file, then tries to open your email program. If that fails, a dialog shows the saved file,
the address to send it to and a **Copy address** button. See [Desktop](desktop.md#reporting-a-bug).

Sharing text offers a choice between **Copy** and **Save** (see [Platform differences](platform_differences.md#files-and-sharing)).

## Linux setup {#linux-setup}

AndBible has a native Linux app. See [Linux](#linux) above.

## Windows setup {#windows-11-setup}

AndBible has a native Windows app. See [Windows](#windows) above.

## Using AndBible on a computer {#mirroring-and-using-app-on-any-computer}

Use the desktop app on [Windows or Linux](desktop.md), or the [App Store](#mac) app on an Apple Silicon Mac.
