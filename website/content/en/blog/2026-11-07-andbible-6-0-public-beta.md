---
title: 'AndBible 6.0 public beta: one app for Android, iPhone, iPad, Mac, Windows and Linux'
date: '2026-11-07'
slug: andbible-6-0-public-beta
summary: AndBible 6.0 is in public beta, sooner than planned. It is one app for Android, iPhone, iPad, Mac, Windows and Linux, with a new shared interface and sync between all of them. Here is how to join the beta on your device and how to tell us what you find.
categories:
- New features
tags:
- beta
- news
- ios
- desktop
image: blog/2026/11/andbible-6-0-public-beta.webp
image_alt: 'AndBible 6.0 public beta on Android, iPhone, iPad, Mac, Windows and Linux: a phone and the Linux desktop app showing the Bible'
---

Today I am opening the **public beta of AndBible 6.0**. It is the biggest change in AndBible's
history: one shared app for **Android, iPhone, iPad, Mac, Windows and Linux**. The Android, Windows
and Linux betas are available now; the iPhone and iPad beta (which also runs on Apple Silicon Macs)
follows in a few days.

## Faster than planned

This is not what I expected to be writing this autumn. In July the separate
[iOS app opened its public beta](/2026/07/17/andbible-for-ios-the-public-beta-is-here/) as an early
MVP, and in September it [reached the App Store](/2026/09/26/andbible-for-ios-now-available-on-the-app-store/).
A month ago, in the [roadmap for Q4 2026 and Q1 2027](/2026/10/09/the-road-ahead-q4-2026-q1-2027/),
the plan was:

- **Q4 2026:** release the Compose version to **Android users only**, first as a beta, with no new
  features.
- **Q1–Q2 2027:** bring the shared codebase to iOS in a unified release, "when it is ready, not by
  the calendar".
- **WebDAV sync:** to be built after the Compose release.

What is here now, in November:

- The 6.0 beta is the **multiplatform** version, not an Android-only one. The same code runs on
  Android and on iPhone and iPad, and the iOS beta of it opens in a few days.
- **Windows and Linux** are new platforms that were not in any roadmap.
- **WebDAV sync** is already in, next to Nextcloud on every platform, iCloud Drive on Apple devices
  and Google Drive on Android.

One big factor in this pace is JailBee, my own side project, which I develop partly at my
workplace, Gisgro Oy. It runs each piece of development work in its own isolated container and
lets me coordinate many of them at once, so large efforts like this port move more smoothly than
before. Like AndBible, [JailBee](https://jailbee.gisgro.io) is open source under the GPL-3.0
licence.

## The whole interface, gone through screen by screen

The largest part of this release is one you could not see until now. Since early summer, when the
UI modernisation work started, I have gone through AndBible's whole interface view by view. In
July every screen was rebuilt in Compose and checked against the classic one. August went into
round after round of comparing old and new screens side by side on a real device. In September
the structure underneath changed: one navigation graph instead of separate Android activities, a
restructured reading view and a new dialog system. Since then every section has been verified,
and every defect found has been fixed.

Along the way a large part of the interface was redesigned, not just copied: Material 3
throughout, clearer menus and dialogs, and many small usability improvements in places that had
aged. It has been long and largely invisible work. With this beta it finally reaches you, and the
same interface is now shared by every platform, so your Bibles, bookmarks, notes and StudyPads
work the same way wherever you open them.

## What is new

- **Every platform.** AndBible now runs on iPhone, iPad, Mac, Windows and Linux as well as on
  Android. On a Mac it is the iPad app, on any Mac with Apple Silicon.
- **A new interface,** rebuilt with Compose Multiplatform and shared between the platforms.
- **Sync between platforms.** Nextcloud and WebDAV sync work everywhere. iCloud Drive is available
  on iPhone, iPad and Mac, Google Drive on Android (not in the F-Droid build).
- **Background activity:** syncing, downloads, search indexing and AI agent runs show in an
  activity list, and finished or failed work is reported with a notification.
- **Reading plans** are now included in synchronization.
- **Search:** *Find all occurrences* no longer opens a separate full-screen results page.

<div class="gallery gallery--narrow" markdown>

![A formatted note on a bookmark, on an Android phone](/media/blog/2026/11/6-0-beta-phone-note.webp)

![Bookmarks with labels on an Android phone](/media/blog/2026/11/6-0-beta-phone-bookmarks.webp)

</div>

<div class="gallery" markdown>

![A StudyPad beside the Bible text on an Android tablet](/media/blog/2026/11/6-0-beta-tablet-studypad.webp)

![Two Bibles side by side in night mode on a tablet](/media/blog/2026/11/6-0-beta-tablet-night.webp)

</div>

![AndBible 6.0 on Linux: the King James Version with Strong's numbers beside a Greek dictionary entry](/media/blog/2026/11/6-0-beta-linux-desktop.webp)

There are many more fixes and improvements. The full list is on the
[Release 6.0](/docs/releases/release_6_0/) page.

## How to join the beta

**Android.** Join the [beta programme on Google Play](https://play.google.com/apps/testing/net.bible.android.activity)
with the Google account of your phone or tablet. Google Play then updates AndBible to the beta.
You can leave the programme on the same page at any time and go back to the stable version at its
next update. If you do not use Google Play, download the `andbible-<tag>.apk` file (not `calculator-…`) from the newest
*Beta release* on our [GitHub releases page](https://github.com/AndBible/and-bible/releases).

**Windows and Linux.** AndBible for the desktop is new in 6.0. Download the Windows installer
(`.msi`) or the Linux `.deb` package or AppImage from the newest *Beta release* on the
[GitHub releases page](https://github.com/AndBible/and-bible/releases). Both are for 64-bit
computers. The [Getting started](/docs/getting_started/#windows) guide walks you through the
installation, and [AndBible on Windows and Linux](/docs/desktop/) has the details.

<!-- if ios_beta -->
**iPhone, iPad and Mac.** Install Apple's *TestFlight* app, then open our
[TestFlight public beta]({{ testflight }}) link on your iPhone, iPad or Apple Silicon Mac and accept
the invitation. The beta replaces the App Store version on that device. See
[Getting started](/docs/getting_started/#iphone-and-ipad).
<!-- else -->
**iPhone, iPad and Mac.** The 6.0 beta for Apple devices is **coming in a few days** through
Apple's TestFlight, as soon as I have finished testing it on real devices. This post and
[Getting started](/docs/getting_started/#iphone-and-ipad) will have the link when it is open.
Until then the App Store has the current AndBible for iOS.
<!-- end -->

As always, AndBible is free and open source, with no ads and no tracking, on every platform.

## Things work a little differently on each platform

Most of AndBible is the same everywhere, but not everything: for example which sync services you
can use, how files are shared and how updates arrive. The
[Platform differences](/docs/platform_differences/) page lists them.

## Please tell us what you find

A beta is a version for everyday use, but it will have rough edges, and that is exactly why we
need you. When something does not work:

- In the app, open the menu, then *Help & tips*, then *Feedback / bug report*. The report includes
  the details we need to find the problem. On iPhone and iPad the same action is in Settings.
- Or open an issue on [GitHub](https://github.com/AndBible/and-bible/issues/new/choose).

Please tell us your device and operating system, and what you were doing when the problem happened.
More about reporting is on the [Support](/docs/support/#reporting-issues) page.

Thank you for testing, and for helping AndBible reach every device.

God bless,  
Tuomas Airaksinen  
Lead developer, AndBible Open Source Project
