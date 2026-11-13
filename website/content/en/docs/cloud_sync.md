# Cloud Sync

*AndBible* can synchronize your bookmarks, notes, labels, study pads, and workspaces
across multiple devices using Google Drive, iCloud Drive, Nextcloud or your own WebDAV server.
Which backends are offered depends on the platform; see [Platform differences](platform_differences.md).

Sync is automatic — once set up, changes are synchronized when you open the app,
every few minutes while it is open, and when you leave or exit it (see
[How Sync Works](#how-sync-works)).

On desktop, the sync when you close AndBible sends only your database changes (bookmarks, workspaces and so on);
documents are synchronized while the app is open. If syncing takes longer when you close AndBible, a status screen appears.
Choose **Quit now** to stop waiting and close; changes not yet sent will need to sync later.

To start a sync yourself, open the top-right three-dot menu and choose **Sync now**.
The command is available once at least one data category or document sync is enabled,
and is disabled while a sync is running. Document transfers still follow your automatic
transfer settings. On Android, an unavailable network shows a short message instead.

On iOS, sync errors and recovery notices appear as in-app messages. If a message
asks you to update AndBible to keep syncing, update the app before trying again.
Errors raised during startup remain queued until the app can display them.

## Requirements

- Android 6.0 or newer, iOS or iPadOS 16 or newer, Windows or Linux
- A Google account (for Google Drive sync), an iCloud account (for iCloud Drive sync on iPhone, iPad and Mac), a Nextcloud server, or a WebDAV server
- Internet connection

!!! note

    Google Drive sync is not available in the F-Droid build. If you installed
    *AndBible* from F-Droid, you can use Nextcloud instead.

## Setting Up Sync

1. Open the top left main menu (☰).
2. Tap `Sync settings`.
3. Choose your sync adapter: **Google Drive** (default on Android), **iCloud Drive** (iPhone, iPad and Mac), **Nextcloud** or **WebDAV**.
    - Google Drive is also offered on iPhone, iPad, Mac, Windows and Linux.
    - Windows and Linux: signing in to Google Drive opens your system browser, where you choose your Google account and allow *AndBible* to use its own app folder. Return to *AndBible* when the browser page says you are done.
    - iPhone, iPad and Mac: a Google sign-in sheet opens inside the app.
4. Toggle on the data categories you want to sync (see [What Gets Synced](#what-gets-synced)).

If a saved backend is unavailable on this platform (for example, after restoring an Android backup with Google Drive sync on Windows), synchronization is disabled. Sync settings explain that a supported backend needs to be chosen, and the sync switches show as off. No other backend is selected automatically, and neither manual nor background synchronization runs. The saved backend is kept unchanged until you choose a supported backend and configure synchronization for this device.

When enabling sync for the first time:

- If **no sync data exists** in the cloud yet, your local data will be uploaded
    automatically.
- If **sync data already exists** (e.g. from another device), a dialog will
    appear with four options:

    - **Merge data** — keeps both the data on this device and the data in the
        cloud. Where the same item was changed on both sides, the newer change
        is kept. This device’s data is also sent to your other devices. If
        anything goes wrong, this device’s data is restored as it was and sync
        stays off for that category.
    - **Copy from Cloud to this device** — downloads the cloud data and replaces
        your local data with it.
    - **Copy from this device to Cloud** — replaces the cloud data with this
        device’s local data.
    - **Cancel & disable synchronization** — cancels and disables sync for this
        category.

    A confirmation dialog will follow to make sure you understand the
    consequences.

### Setting Up Nextcloud

If using Nextcloud instead of Google Drive:

1. In Sync settings, change the `Sync adapter` to **Nextcloud**.
2. Enter your Nextcloud server URL, username, and password.
3. Optionally specify a folder path on the server.
4. Toggle on the data categories you want to sync.

When you open a text setting such as the server address, username or password,
its editor is ready for typing without an extra tap or click. On touch devices,
the on-screen keyboard opens automatically.

Nextcloud and WebDAV server addresses work with or without a trailing `/`.
AndBible removes trailing slashes and surrounding whitespace when saving the
address, and also handles trailing slashes in previously saved addresses.

Nextcloud and WebDAV servers require a secure `https://` address on every
platform (Android, iOS and desktop). Plain `http://` is not accepted, not even
for a server on your local network. AndBible refuses an `http://` address both
when you save it and when it signs in. If you saved an `http://` address
earlier, sync does not start and AndBible tells you the address must be
`https://`. The saved address is kept, so you can edit it and add the `s`
(or set up HTTPS on your server).

### Setting Up WebDAV

WebDAV lets you synchronize with your own server, a NAS, or any server that
supports WebDAV (RFC 4918), without needing an account with a third-party cloud
service.

1. In Sync settings, change the `Sync adapter` to **WebDAV**.
2. Enter the server address, username, and password.
3. Optionally specify a `Sync folder path`, a subfolder on the server under
    which *AndBible* stores its data. It is created if it does not exist.
4. Toggle on the data categories you want to sync.

Typical server addresses:

| Server | Address |
| --- | --- |
| Nextcloud / ownCloud | `https://HOST/remote.php/dav/files/USERNAME/` |
| Synology NAS | `https://NAS:5006/` |
| Apache / nginx | `https://HOST/dav/` |
| rclone (`rclone serve webdav`) | `https://HOST:PORT/` |
| Koofr | `https://app.koofr.net/dav/Koofr/` |

!!! note

    Only secure `https://` addresses are accepted; an `http://` address is
    rejected. Sign-in uses your username and password (the server chooses Basic
    or Digest authentication automatically).

#### Self-signed certificates

If the server's certificate is not trusted by your device (for example a
self-signed certificate on a home server), *AndBible* shows an
**Untrusted server certificate** dialog with the host, subject, issuer,
validity period, and the SHA-256 fingerprint of the certificate. Compare the
fingerprint with the one on your server, and choose **Trust this certificate**
only if they match.

- The trust is remembered for that server address only. Certificates you have
    installed on the phone as extra certificate authorities are not used. (On
    Android 6, certificates you installed on the phone may still be accepted by the
    system; Android excludes them only from Android 7 onwards.)
- If the server's certificate changes later, sync pauses with a message. The
    prompt to confirm the new fingerprint appears the next time *AndBible* is opened
    or syncs in the foreground.
- Whenever a certificate is trusted for the WebDAV provider, Sync settings shows a
    **Server certificate** row where you can forget the trusted certificate. The
    row is disabled while you are signed in.

!!! tip

    The first syncs with some servers can take a little longer, until *AndBible*
    has learned how the server reports folder changes.

### iOS background refresh

On iOS, background refresh uses saved credentials and previously trusted
certificates only. A new certificate must be confirmed in the foreground; the
confirmation shows its host, subject, issuer, validity dates, and SHA-256
fingerprint. Trusting it does not disable certificate checks for other servers.
Complete the first synchronization in the foreground, including any question
about restoring cloud data or creating a new synchronization folder.

After a device restart, background refresh waits until protected data becomes
available after the first unlock. A skipped or failed refresh requests another
opportunity, but iOS decides when background work can actually run.

## What Gets Synced

Each category has its own toggle in Sync settings and can be turned on or off
independently.

**Bookmarks** (toggle: Bookmarks)
:   Bookmarks, highlights, notes, labels, and study pads — for both Bible
    and non-Bible documents.

**Workspaces** (toggle: Workspaces)
:   Workspace configurations, window layouts, and current document positions.

**Reading Plans** (toggle: Reading Plans)
:   Your reading plans and their progress (which readings you have completed).

**My Documents** (toggle: My Documents)
:   Your personal documents and their content. See [My Documents](my_documents.md).

**AI Settings** (toggle: AI Settings)
:   Custom AI prompts and provider configurations (provider type, model,
    endpoint). API keys, cache, and usage data are **not** synced – you need
    to enter your API key on each device separately. See [AI Bible Study Assistant](ai.md) for details.

**Reading Progress** (toggle: Reading Progress)
:   Memorized verses and your chapter reading records. See
    [Reading Progress](reading_progress.md) and [Memorize](memorize.md).

**Documents** (toggle: Documents)
:   Installed Bibles, commentaries, dictionaries and other modules can be synced
    as a separate category. Unlike the categories above (which sync small data
    records), this copies whole document files. See [Document Sync](document_sync.md) for
    details.

**Not synced:**
:   App settings, preferences, AI API keys, and AI cache are not synced.

## How Sync Works

- Sync runs when the app is opened or brought back to the foreground, then
    every 5 minutes while it stays open and the device is online. Each of these
    syncs also fetches the changes made on your other devices.
- Sync also runs when you leave or quit the app, and (on Android, iPhone, iPad and
    Mac) when the network comes back after being offline.
- On iPhone, iPad and Mac, iOS can also give *AndBible* time to sync in the
    background; see [iOS background refresh](#ios-background-refresh).
- See [Platform differences](platform_differences.md#sync) for the differences between platforms.
- Only changes since the last sync are uploaded — not the entire database each time.
- If two devices change the same item, the most recent change wins automatically
    (based on timestamps).

## Multi-Device Usage

Each device is identified separately. When multiple devices are synced:

- Changes from all devices are merged automatically.
- If the same bookmark or workspace is modified on two devices between syncs,
    the last change (by timestamp) takes precedence.
- There is no manual conflict resolution — the process is fully automatic.

## Sharing Data Between Android and iOS

All release versions of *AndBible* use the same synced data: the Android app
from the Play Store, F-Droid, GitHub, and the other stores, the discrete
(calculator) version, the Accrescent version, and the iOS app. The shared
folder applies to every sync backend: NextCloud, WebDAV, and Google Drive.
Google Drive is also offered on iPhone, iPad, Mac, Windows and Linux, so signing in with the same Google account shares the data with Android. iCloud Drive is available only on iPhone, iPad and Mac; see [Platform differences](platform_differences.md).

If you already use sync on the **standard Android** version, nothing changes
when you update: sync keeps working as before.

If you use the **discrete**, **Accrescent**, or **iOS** version, sync moves to
the shared folder once after you update:

1. Your enabled data categories stay on. The next time sync runs, each of them
    moves to the shared folder automatically. You do not need to enable them
    again, unless you cancel the question in step 2.
2. If the shared folder already contains data, for example from your Android
    device, the app asks what to do, as described in
    [Setting Up Sync](#setting-up-sync): **Copy from Cloud to this device**,
    **Copy from this device to Cloud**, or **Cancel & disable synchronization**.
    Be careful with **Copy from this device to Cloud**: it replaces the data in
    the shared folder, including the data your other devices (for example the
    standard Android app) have synced there.
3. If the shared folder is empty, a new one is created from the data on this
    device.

The old folders on the server are left as they are. *AndBible* does not delete
them, but it no longer reads or writes them, so you can remove them yourself
when you no longer need them. Their names start with the app id of the version
that used them:

- Discrete: `com.app.calculator-sync-…`
- Accrescent: `org.andbible.andbible-sync-…`
- iOS: `org.andbible.ios-sync-…`

The shared folders are named `net.bible.android.activity-sync-…`.

!!! note

    Test builds with a suffixed application id (for example, a debug build)
    sync to their own separate folder and do not share data with the regular
    release versions.

## Resetting Sync

To disconnect from cloud sync:

1. Open `Sync settings`.
2. Tap `Sign Out`.
3. Confirm the reset.

This signs out, disables synchronization, and resets related settings. Your
local data is kept intact. You can set up sync again at any time.

## Tips

### Compacting Cloud Storage

Over time, the sync data in the cloud can grow as incremental change patches
accumulate. To compact the cloud storage:

1. Make a [backup](backup_restore.md) first, just in case.
2. On one device, open `Sync settings` and tap `Sign Out`.
3. Set up sync again and choose **Copy from this device to Cloud**. This
    replaces the accumulated patches in the cloud with a clean snapshot.
4. On your other devices, sync will detect that the cloud data has changed.
    When prompted, choose **Copy from Cloud to this device** to restore from the
    clean snapshot.

## Privacy and Security

- **Google Drive:** *AndBible* uses the app-specific `AppData` folder on Google
    Drive (also on iOS, Windows and Linux). It cannot access any other files on your Drive, and other apps cannot
    access *AndBible*’s sync data.
- **Data sent:** Only your bookmarks, labels, notes, study pads, workspaces,
    and window configurations are uploaded — no personal information beyond what
    you explicitly create in the app.
- **Credentials:** Authentication credentials are stored only on the device
    and are never included in backups.

!!! note

    *AndBible* developers are not responsible for any data loss or damage that may
    occur during the synchronization process. Regular [backups](backup_restore.md)
    are recommended.
