# Frequently Asked Questions

<span id="contents"></span><span id="id9"></span><span id="id10"></span><span id="id11"></span><span id="id12"></span><span id="id13"></span><span id="id14"></span><span id="id15"></span><span id="id16"></span><span id="id17"></span><span id="id18"></span><span id="id19"></span><span id="id20"></span>

## I can’t find ESV any more in Downloads. What’s wrong? {#i-can-t-find-esv-any-more-in-downloads-what-s-wrong}

The publishers of the ESV have unfortunately decided it is no longer in their
interest to publish towards the SWORD platform (by Crosswire Bible Society)
which *AndBible* uses.

If you still have ESV on another device, you can transfer it by backing up the
module and restoring on the new device. See [Backup and Restore](backup_restore.md) for details.

## Please add module X to AndBible!

Modules in *AndBible* are provided via the JSword engine, which uses SWORD modules
from [Crosswire](https://crosswire.org) and several other freely available
module repositories. You should primarily make your requests to the Crosswire
modules team.

You can contact them via their
[email list](https://www.crosswire.org/mailman/listinfo/sword-devel)
or their other [contact methods](http://crosswire.org/contact/).
See also their [FAQ](https://wiki.crosswire.org/EnduserFAQ) and their
[instructions on contacting copyright holders](https://wiki.crosswire.org/Copyright).

If the text is not copyrighted (i.e. it is public domain), there may be a
shortcut to get it into *AndBible* quickly. In that case, please
[contact us](mailto:help.andbible%40gmail.com).

If you know that a module is available in MySword or MyBible applications, it
may be possible to install it in *AndBible* too — see [Documents](documents.md) for details
on third-party module format support.

## I found a text issue in a Bible / Commentary module

*AndBible* developers don’t fix module issues. Modules used by *AndBible* are provided
primarily via the JSword engine which uses SWORD modules made by multiple
providers. You can determine who provides the module from the Download Documents
list. If you have an issue we encourage you to contact them directly.

- **Crosswire:** Use their [issue tracker](https://tracker.crosswire.org/projects/MOD/).
    You can also get in touch with them via their
    [email list](https://www.crosswire.org/mailman/listinfo/sword-devel)
    or other [contact methods](http://crosswire.org/contact/).
- **eBible:** Submit a message through their
    [online form](https://ebible.org/cgi-bin/contact.cgi).
- **AndBible/AndBible Extra:** Raise an issue on our
    [GitHub issues page](https://github.com/AndBible/and-bible/issues).

## I have an older phone. Can I use AndBible?

The current version of *AndBible* requires Android 6.0 (Marshmallow) or newer.
If you have an older device, you can download older versions:

| Android Version | AndBible Version | Download |
| --- | --- | --- |
| 2.3 (Gingerbread)+ | 2.9.5 (unsupported) | [Download](https://github.com/AndBible/and-bible/releases/tag/build-02.09.05) |
| 4.4 (KitKat)+ | 3.2.343 (unsupported) | [Download](https://github.com/AndBible/and-bible/releases/tag/v3.2.343) |
| 5.0 (Lollipop)+ | 4.0.687 | [Download](https://github.com/AndBible/and-bible/releases/tag/production-687) |
| 6.0 (Marshmallow)+ | Latest | [Download](https://github.com/AndBible/and-bible/releases/latest) |

## How do I change the voice of the speech synthesis?

How the voice is changed varies between different TTS engines.

If you are using Google Text-to-Speech Engine (which you can install from
[Google Play](https://play.google.com/store/apps/details?id=com.google.android.tts)),
you can change the voice as follows: go to your device’s system TTS settings,
tap the Settings icon, then “Install voice data”, select your language, and
choose a voice.

See also the [Speak](speak.md) page for more about the Text-to-Speech feature.

## Is there a version for iOS (iPhone / iPad)?

Yes! *AndBible* is now available on the
[App Store](https://apps.apple.com/us/app/andbible-bible-study/id6774904527)
for iPhone, iPad, Apple Silicon Macs and Apple Vision Pro. It is close to
feature-complete, though the Android and iOS versions are currently built on
separate codebases, so some differences remain while the two are unified.

If you’re still on an older Android device and prefer not to switch:

- Consider buying an inexpensive Android tablet to use *AndBible*.
- You can [run the app on Linux using Waydroid](getting_started.md#linux-setup).
- You can use Android emulators like [BlueStacks](https://www.bluestacks.com/)
    on Windows or macOS.

## How do I download more Bibles, commentaries, etc.?

See [Documents](documents.md) for instructions on downloading and managing documents.

In short: from the top left main menu (☰), tap *Download documents*,
then search for and install the documents you need.

## How do I copy my notes and bookmarks to a new phone?

Back up on one device and restore on the other. See [Backup and Restore](backup_restore.md)
for detailed instructions.

## Where do the Bible translations come from?

All documents available in *AndBible* are either in the public domain or licensed
for distribution. They come from these sources:

- [Crosswire](https://crosswire.org)
- [IBT Russia](https://ibtrussia.org)
- [eBible](https://ebible.org)
- [STEPBible](https://public.modules.stepbible.org)
- [AndBible](https://andbible.github.io)

## How do I downgrade from 5.0 to 4.0 (or 4.0 to 3.3)? {#how-do-i-downgrade-from-5-0-to-4-0-or-4-0-to-3-3}

!!! warning

    **Do not uninstall the app yet.** Uninstalling will remove all data,
    including the automatic backup that was created during the upgrade.

1. Go to `Main menu > Backup & Restore`.
2. Under `Import & Export`, select an earlier backup file to export
    (files named `dbBackup-57-*.db` for the pre-5.0 backup, or
    `dbBackup-33-*.db` for the pre-4.0 backup). Back it up somewhere safe.
3. Optionally, also back up your documents.
4. Uninstall the app.
5. Install the earlier version:
    [4.0](https://github.com/AndBible/and-bible/releases/tag/production-687) or
    [3.3](https://github.com/AndBible/and-bible/releases/tag/production-400)
    from GitHub Releases. You may need to allow installs from unknown sources.
6. From `Main Menu > Backup & Restore`, restore the backup file.

!!! note

    To prevent automatic updates, check your auto-update settings in Google Play
    or your app store. Using versions other than the latest is unsupported.

## How to get the paid NET module working?

1. Purchase “NET Bible Crosswire Bible Society Sword module (Premium Version)”
    at [bible.org](https://store.bible.org/store/product/108).
2. You will receive an email with a download link and an unlock code.
3. Download the SWORD module file from the linked page.
4. In *AndBible*, use `Backup & Restore > Restore > Documents`, then tap
    `Restore From` and select the downloaded file.
5. Go to `Choose Document` in the main menu. You will see the module with
    a red lock icon. Tap on it and enter the unlock code from your email.

## Is there a way I can support the project?

Yes! You can contribute with your skills (code, translations, documentation)
or sponsor development work at [shop.andbible.org](https://shop.andbible.org/).

See our [contribution guide](https://github.com/AndBible/and-bible/wiki/How-to-contribute)
for all the ways you can help.
