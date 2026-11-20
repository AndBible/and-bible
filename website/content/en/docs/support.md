# Support

Have questions? Found a Bug? Want to Suggest a feature?

Please refrain from asking questions or reporting bugs via the Play Store.
Use the following support channels instead:

## General Questions

You can ask general questions on our [Telegram](https://t.me/andbible)
or [Matrix](https://matrix.to/#/#andbible:matrix.org) user groups.
These groups are bridged together (connected), so you can choose which platform
you prefer.

Alternatively, you can reach out to our development team via
[email](mailto:help.andbible%40gmail.com) or
[GitHub](https://github.com/AndBible/and-bible/issues/new/choose)

In the app, the main menu (☰) rows `Questions?` and `How to contribute?` (in the *Information*
group) open the support and contribution pages in your browser; `Help & tips` opens the help screen
(described below). See [Main menu](navigation.md#main-menu) for all rows.

### Help & tips

`Help & tips` is a full screen with two tabs. It opens on the **User guide** tab:

- **User guide** is the table of contents of this manual. Its first row, **Watch the tutorial videos in
  order**, opens the whole series as a numbered list; Up or Back returns to the table of contents. Select a
  page row (document icon, *Read in the user guide*) to open it in your browser. Where tutorial episodes are
  available, their video rows appear alongside the page link. Section,
  page, and episode titles are localized, but the manual pages and videos themselves are in English.
  Unpublished episodes open the "Videos coming soon" placeholder; published episodes open their own videos.
- **Tips** has short help topics, each with a link to its tutorial video and to the matching page of this
  manual, and a link to the full documentation.

### Help in the user guide {#help-in-the-user-guide}

Many screens, menus and help dialogs have a **Help in the user guide** item (in the screen's ⋮ menu, or as a
link in a help dialog). It opens `Help & tips` on the **User guide** tab, scrolled to the page that covers that
screen, with the page and its tutorial videos highlighted (a tinted background and a bar at the start edge; in
monochrome mode only the bar). From there, open the page in your browser or play its video. Up or Back returns
to the screen you came from. These items stay inside the app, so the discrete build does not ask before
opening them; the guide page and the video still ask, as any external link does.

Tutorial videos open on YouTube. When an episode has subtitles in the language of the app, they are turned
on for you; otherwise the video is in English and the link says so. Unpublished episodes use the placeholder
video's available subtitles. The YouTube interface language follows the selected subtitle track. Chinese
without a region uses Traditional Chinese subtitles, as does Chinese (Macao); an explicit Simplified Chinese
script still selects Simplified Chinese. Cantonese is not treated as Mandarin Chinese.

## Reporting Issues

Please report bugs via the `Feedback / bug report` option (in the *Contact* group) of the top left main menu (☰).
Reporting bugs through the app provides us with more information to fix the issue. You can also report
bugs directly via [GitHub Issues](https://github.com/AndBible/and-bible/issues/new/choose).

On iOS the same `Feedback / bug report` item is in the drawer, as on every platform. The crash recovery screen also
has a report action that works before the app's databases open. Confirm before opening
the Mail composer or, if Mail is not configured, the system share sheet. Nothing is
sent automatically: review the report and attachments before sending, or cancel.
Reports include device/app details, a compressed recent log from the current process,
and the last captured Kotlin crash stack when available. The recent log is kept in
memory, so it does not contain logs from a previous app launch. iOS reports can also
include `ios_runtime_diagnostics.txt.gz`, a separate technical history of recent app,
scene, window and reading-pane lifecycle events from the current and previous retained
app launch only, within the last seven days.
This history uses an allowlist of technical fields, not Bible text, notes, account details,
paths, URLs or arbitrary JavaScript. It is kept in two files of at most 512 KiB each;
because they are recoverable generations of the same history and periodically compacted,
the active distinct history is about 256–512 KiB. Expired records are removed from disk
at startup and file rotation and are always excluded from reports. Events waiting to be written may be lost
if the app is forcibly terminated. A recorded event alone cannot establish the cause of
a hang. Review all attachments before sending. AI bug reports attach the selected
conversation log instead; these logs may contain private information.

An iOS error dialog offers *Send Report*, *Backup & Restore*, and *Skip*. Choosing
Backup & Restore opens the backup screen without sending a report.

## Feature Requests

If you would like to request a specific feature, please request the feature via
[GitHub](https://github.com/AndBible/and-bible/issues/new/choose). When requesting,
please describe the feature clearly.

## Sharing on Android

**Recommend to a friend** (the same on every platform) shows a QR code of
[andbible.org](https://andbible.org), where the downloads for every platform are listed, that a friend
can scan in person. **Share a link** in the same dialog opens the system share sheet with a short
description of AndBible and the same link.
**Rate & Review**, when offered, shows a short dialog about rating and about reporting problems; its confirm
button then opens the standard Play listing, or Samsung Apps for Samsung builds.

**Recommend to a friend** is hidden in discrete mode. Recommendation and rating actions
are disabled while discrete mode is active, to avoid revealing the app's identity.

## Help and sharing on iOS

The iOS drawer's **Help & tips** shows all eight help topics, tutorial and manual links,
and the app version. It does not include the Android development-shop promotion.
**Open Source License** displays the complete licence bundled with the app, so it is available offline.
**Recommend to a friend** works as on Android: it shows a QR code of the homepage and a **Share a link** button.

The iOS **Feedback / bug report** action asks for confirmation before preparing a report with device
and version details, any retained crash details, and a compressed recent app log. If iOS has
delivered a crash diagnostic from the last 24 hours, it is also attached as
`recent_crash_diagnostic.json.gz`. Diagnostics are not sent automatically. JavaScript error
reports use the same consent flow and include the WebView error diagnostic.

The iOS drawer does not currently offer a rating or development-purchase action.

## Help and bug reports on Windows and Linux {#help-and-bug-reports-on-windows-and-linux}

**Feedback / bug report** (in the *Contact* group of the main menu) asks where to save a report
file, then always shows short instructions: send an email to the address shown (**Copy address**),
attach the saved file (its path is shown, with **Copy file path** and **Open folder**), and use the
suggested subject (**Copy subject**). Your email program is then opened if possible. See [Desktop](desktop.md#reporting-a-bug).

Sharing text offers a choice between **Copy** and **Save** (see [Platform differences](platform_differences.md#files-and-sharing)).

## Tutorial Videos

Short walkthroughs of the main features, in the order of the tutorial series, are on the
[videos page](/videos/).

## Tips & Tricks

[Watch the playlist on YouTube](https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mn5N3KSmNsb2IuBbR1XVLke)
