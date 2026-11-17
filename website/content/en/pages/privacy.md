---
title: AndBible Privacy Policy
---

*Last updated: 2026-11-17*

This policy covers the AndBible app on Android, iPhone, iPad, Mac, Windows and Linux, and the
andbible.org website.

## Summary

- AndBible has no user accounts, no advertising, and no analytics or tracking in the app.
- Your bookmarks, notes, Study Pads, workspaces, reading plans and other data are stored on your
  device. The AndBible developers do not run a server that receives or stores this data.
- Data leaves your device only through features you choose to use: cloud sync (Google Drive,
  iCloud Drive, Nextcloud or WebDAV), the AI assistant (with an AI provider you configure),
  downloading documents, sending a bug report, and the update check of the desktop version.
  These are described below.

## Data stored on your device

Everything you create in AndBible (bookmarks, highlights, notes, labels, Study Pads, My Documents,
workspaces, reading plans and reading progress, settings, AI prompts and AI logs) is stored in the
app's own storage on your device. You can export it with Backup & Restore. Uninstalling the app
removes it from the device, except for any backup files you have saved elsewhere.

On Android, if Android's own app backup is turned on for your device, Android may include the
app's databases in that backup. The app's preference storage, where API keys and sync passwords
are kept on Android, is excluded from it.

## Cloud sync (optional)

Cloud sync is off by default. When you turn it on in *Device synchronization*, AndBible copies
the data categories you enable (bookmarks and notes, workspaces, reading plans, My Documents,
AI settings, reading progress, and optionally your installed documents) to a storage service
that you choose and sign in to. The data goes directly from your device to that service; it does
not pass through any AndBible server. AndBible developers have no access to it.

- Data is sent over encrypted (HTTPS) connections. AndBible does not add its own end-to-end
  encryption: the stored data can be read by whoever operates the storage service, under that
  service's own terms.
- Sync passwords and sign-in tokens are stored only on the device where you signed in: in the
  iOS Keychain (not synced to iCloud Keychain), in your operating system's credential store on
  Windows and Linux, and in the app's private storage on Android. They are not synced and are not
  included in AndBible backup files.
- Signing out stops synchronization on that device and keeps your local data. It does not delete
  data already stored in the cloud; you can delete it yourself through the storage service.
- AndBible developers are not responsible for any data loss or damage that may occur during the
  synchronization process.

### Google Drive

- AndBible uses the Google Drive API with the `drive.appdata` permission only. This gives AndBible
  access to its own hidden app data folder in your Google Drive, and to no other files in your
  Drive. Only data used for synchronization is stored in or read from this folder.
- AndBible reads the email address of the Google account you signed in with, to show and remember
  which account is in use. It stays on your device.
- AndBible does not share any Google user data with anyone, and does not use it for any other
  purpose than synchronization.
- Data stored in your Google Drive is subject to Google's privacy policy. You can delete
  AndBible's sync data in Google Drive's settings, under *Manage apps*.

### iCloud Drive (iPhone, iPad and Mac)

- On Apple devices you can choose iCloud Drive as the sync backend. AndBible then stores its sync
  files in its own iCloud container in your iCloud account. AndBible cannot access your other
  iCloud files.
- Signing in uses the iCloud account of your device; AndBible does not see your Apple account
  password.
- Data stored in iCloud is subject to Apple's privacy policy and your iCloud settings.

### Nextcloud and WebDAV

- You can sync to your own or another Nextcloud or WebDAV server. Only secure `https://` server
  addresses are accepted.
- The data is stored on that server, under the control of whoever operates it.

## AI assistant (optional)

AndBible includes an optional AI assistant. It is not active until you add an AI provider with
your own API key and accept the AI disclaimer. AndBible does not include a built-in AI provider or
API key, and the AndBible developers do not run an AI service.

- **Where data goes.** When you run an AI prompt, AndBible sends the request directly from your
  device to the AI provider you configured (for example Google Gemini, OpenAI, Anthropic, xAI,
  Mistral, DeepSeek, Groq, Alibaba Cloud, OpenRouter, or a custom endpoint you enter, which can be
  your own server). It does not pass through any AndBible server. The provider's own terms and
  privacy policy apply to what you send to it, including whether it stores or uses that data.
- **What is sent.** The prompt instructions, the text you selected or typed (for example verses,
  a note you ask it to improve, or your question), and the results of the tools the AI uses
  during the run. Depending on the prompt and the tools you allow, these tools can read installed
  documents (Bibles, commentaries, dictionaries and other books), your bookmarks, notes, labels,
  Study Pads, My Documents and the windows of your workspace, and send the parts they read to the
  provider. You can switch off tools globally or per prompt, for example to keep the AI from
  reading your bookmarks.
- **Model lists.** For providers that offer it, AndBible also asks the provider for its list of
  available models, using your API key.
- **API keys** are stored only on the device where you entered them: in the iOS Keychain (not
  synced to iCloud Keychain), in your operating system's credential store on Windows and Linux,
  and in the app's private storage on Android. They are not synced and are not included in
  AndBible backup files.
- **AI logs.** AndBible saves the AI conversation logs and token usage and cost estimates on your
  device, so you can review them. You can delete the logs, or have them deleted automatically
  after a number of days. The logs are not synced.
- **Sync of AI settings.** If you enable the *AI Settings* sync category, your custom prompts,
  provider configurations (provider type, endpoint and models, without API keys) and usage
  statistics are synced through the backend you chose. Results you save in the app, such as
  bookmarks, notes, Study Pads or documents the AI created, are ordinary app data and sync like
  your other data.

AI results can be wrong. Do not use the AI assistant with content you do not want to share with
your AI provider.

## Downloading documents

When you download Bibles and other documents, or refresh the list of available documents, AndBible
connects to the document repositories: AndBible's own repositories and those of CrossWire, eBible,
IBT, STEP Bible (Tyndale) and others listed in the app, plus any custom repository you add. Like
any web server, these servers see your IP address and which files are requested. No account or
personal information is sent.

## Bug reports

Bug reports are never sent automatically. When you choose to send one, AndBible prepares an email
(or, where no email app is available, a file and instructions) that you can review before
sending. Depending on the platform, it contains device and app version details, a log of the app's
recent activity (on Android only the app's own log, not that of other apps), error details, and on
Android a screenshot of the app. An AI bug report instead attaches the conversation log of that AI run,
which can contain the text sent to and received from the AI provider. Reports are sent to the
AndBible developers by email and are used only to fix problems. If you contact us for support by
email, Telegram, Matrix or GitHub, we see what you send us there.

## Update check (Windows and Linux)

Desktop builds downloaded from GitHub ask whether to check for updates. If you agree, AndBible
periodically asks GitHub for the list of releases. GitHub sees your IP address and the app
version; no device identifier is sent. You can change how often it checks, or turn it off, in
*Application preferences*.

## Other device features

- **Text-to-speech** uses the speech engine of your device or operating system. Whether that
  engine processes text on the device or online depends on the engine you have chosen.
- **Notifications** are used for reading plan reminders, speech playback and sync progress. They
  are created on the device.
- **Motion sensors** on iPhone and iPad are used only for tilt-to-scroll, on the device.
- Links you share or open (for example `read.andbible.org` passage links) contain only the Bible
  reference and document name.

## App stores

The store you install AndBible from (for example Google Play, the App Store, F-Droid or another
store) may collect data about installs and app usage under its own privacy policy. Depending on
your device settings, Google Play and the App Store can share aggregated statistics and crash
reports with developers. AndBible itself adds no such reporting.

## The andbible.org website

The website uses Google Analytics to see which pages are useful. Visitors in Europe are asked for
consent first, and nothing is loaded or stored until they accept. YouTube videos on the site are
loaded from youtube-nocookie.com only when you click to play them.

## Children

AndBible does not knowingly collect personal information from anyone, including children.

## Changes and contact

If this policy changes, the updated version is published on this page. Questions about this
policy: [help.andbible@gmail.com](mailto:help.andbible%40gmail.com).

Permanent location of this privacy policy: [https://andbible.org/privacy/](/privacy/)
