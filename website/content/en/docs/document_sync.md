# Document Sync

In addition to syncing your data — bookmarks, workspaces, reading plans, My
Documents, AI settings and reading progress (see [Cloud Sync](cloud_sync.md)) — *AndBible* can
also synchronize your installed **documents** — Bibles, commentaries,
dictionaries and other modules — across your devices, using the same cloud
account (Google Drive or Nextcloud).

When a document is installed on one device — by *any* method (downloaded from a
repository, sideloaded as a zip, or imported as a MyBible, MySword, e-Sword or
EPUB module) — the complete document is copied to the cloud and re-installed on
your other devices. Enabling document sync on a new device also brings down
everything already stored in the cloud.

!!! note

    The whole document is copied to the cloud, not merely a reference to where it
    was downloaded from. This is deliberate: sideloaded and custom-repository
    documents often cannot be re-downloaded reliably on another device, so *AndBible*
    keeps a complete copy. As a result, the space used in your cloud account grows
    with the size of your document library.

## How It Differs from Data Sync

Documents is a separate category from the data categories (bookmarks,
workspaces, reading plans, and so on) described in [Cloud Sync](cloud_sync.md). It shares
the same cloud account and runs on the same background sync cycle, but it moves
whole document files rather than small database changes, and it offers more
per-device control (see below).

## Requirements

- A cloud account already set up — see [Cloud Sync](cloud_sync.md) for how to sign in to
    Google Drive or Nextcloud.
- Internet connection.

!!! note

    Google Drive sync is not available in the F-Droid build. If you installed
    *AndBible* from F-Droid, you can use Nextcloud instead.

## Enabling Document Sync

1. Open the top left main menu (☰).
2. Tap `Sync settings`.
3. Make sure you are signed in to a cloud account.
4. Under **Synchronization categories**, toggle on **Documents**.

When you turn on **Documents**, *AndBible* scans your device and the cloud and shows a
summary dialog of what it will transfer — for example how many documents would be
uploaded and downloaded, with their approximate sizes. Confirm to start; the
transfers then run in the background.

If there is nothing to transfer yet, the dialog says so — newly installed
documents will still sync automatically from then on.

!!! note

    Once enabled, document sync is **automatic**. There is no separate “automatic”
    switch — the per-device toggles below shape *how much* it does automatically.

## Per-Device Settings

While document sync is enabled, a **Document sync** section appears in Sync
settings with the following per-device options. These settings are local to each
device and are **not** themselves synced.

**Download automatically**
:   Automatically download new and updated documents from the cloud to this
    device.

**Upload automatically**
:   Automatically upload documents installed on this device to the cloud. Turn
    this off on a device that should receive the cloud library but never push its
    own documents (for example a device used to test throwaway modules).

**Apply removals automatically**
:   Automatically remove documents from this device when they are removed on
    another device.

**Sync documents on Wi-Fi only**
:   Automatic document transfers wait for an unmetered (Wi-Fi) connection.
    *Manual* actions always proceed regardless of this setting.

**Synced documents**
:   Opens the management view (see below).

!!! note

    All three “automatic” toggles default to **on**, so out of the box a device
    syncs everything in both directions. Turning a toggle off only affects
    *automatic* behaviour — you can always perform the corresponding action
    manually from the management view.

## Managing Synced Documents

Tap **Synced documents** in Sync settings to open the management view — a unified
list of the documents installed on this device together with those stored in the
cloud. Each row shows the document’s type icon, name, version, size, and a status
line such as:

- **Synced** — present on this device and in the cloud, up to date.
- **On this device only** — installed here but not yet in the cloud.
- **In cloud only** — available in the cloud but not installed here.
- **Update available** — a newer version is in the cloud.
- **Blocked on this device** — excluded from syncing to this device.
- **Won’t sync to cloud** — a device-only document you’ve chosen not to upload.
- **Removed from cloud** — a document that was removed elsewhere (shown only when
    *Show removed documents* is enabled).

### Filtering

Use the search box and the two spinners at the top to narrow the list by name,
status (**All**, **Installed**, **In cloud**, **Updates**, **Blocked**, **Only on
this device**, **Removed**) and document category (Bibles, commentaries, etc.).

### Per-Document Actions

Tap a document to open its menu. Only the actions relevant to that document are
shown:

**Download**
:   Download and install this document from the cloud (available for cloud-only
    documents or when an update is available).

**Push to cloud**
:   Upload this device’s copy to the cloud (available when the document is on this
    device only, or is newer here than in the cloud).

**Remove**
:   Remove the document from the cloud. With document sync **on**, this removes it
    from *all* your devices (including this one). With document sync **off**, it
    removes only the cloud copy and keeps the local one. *AndBible* never removes a
    document if it would leave you without a Bible.

**Block / Do not sync to cloud**
:   For a cloud-backed document, **Block** stops it from downloading to this
    device. For a device-only document, **Do not sync to cloud** stops it from
    being uploaded. Both use the same per-device block list; the wording just
    adapts to the situation. Use **Unblock** / **Sync to cloud** to reverse it.

Manual actions always work, regardless of the automatic toggles — they are
explicit choices you are making.

### Selecting Multiple Documents

Long-press a row to enter selection mode, then tick several documents to act on
them together (download, upload, remove, block, and so on). An action is offered
whenever at least one selected document supports it, and is applied only to the
documents it applies to.

### Sync Now

The **Sync now** option (in the overflow menu) runs a full manual sync
immediately. It first previews what will be transferred and lets you choose which
directions to run — **Download**, **Upload**, **Delete** — each showing how many
documents (and how much data) are involved. A manual sync ignores the automatic
toggles and the Wi-Fi-only setting.

## Removed Documents

When a document is removed from the cloud, *AndBible* leaves a small marker (a
“tombstone”) behind so that your other devices know to remove it too, rather than
uploading it again.

By default these removed documents are hidden. Enable **Show removed documents**
in the management view’s overflow menu to see them. For a removed document you can:

**Restore to cloud**
:   Re-upload the document to the cloud from a device that still has it installed.

**Remove from cloud history**
:   Permanently delete the removal marker. Note that if the document is still
    installed on another device, that device may upload it back to the cloud on its
    next sync — the marker is what prevents that.

!!! note

    Because removing a document from the cloud deletes the stored copy (only the
    marker remains), a removed document can be brought back only by restoring it
    from a device that still has it installed — not by re-downloading it from the
    cloud.

## Re-scan from Cloud

If the management view ever seems out of date, use **Re-scan from cloud** in the
overflow menu. This discards *AndBible*’s local listing cache and fetches a fresh,
authoritative list of everything in the cloud.

## Cloud Storage Used

The space taken up by your synced document archives is included in the
cloud-storage figure shown in Sync settings, alongside the space used by your
synced bookmarks and workspaces. Because whole documents are stored, this can grow
significantly with a large library.

## Signing Out

Signing out of cloud sync (see [Cloud Sync](cloud_sync.md)) stops any document transfer in
progress and clears this device’s document-sync settings, block list, and cached
listing. Your installed documents themselves are **not** deleted from the device.
Setting up sync again starts fresh — which is correct, as it may be a different
cloud account.

!!! note

    *AndBible* developers are not responsible for any data loss or damage that may
    occur during the synchronization process. Regular
    [backups](backup_restore.md) are recommended.
