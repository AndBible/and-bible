---
title: 'New feature: Document Sync'
date: '2026-07-04'
slug: new-feature-document-sync
summary: If you use AndBible on more than one device — a phone and a tablet, an old phone and a new one — you know the small frustration of setting…
tags:
- device synchronization
- documents
- new features
- video
image: blog/2025/08/new-andbible-features.webp
image_alt: 'New feature: Document Sync'
---

https://www.youtube.com/watch?v=hhF_565fezM

## Your Bible library, on every device: introducing Document Sync

If you use AndBible on more than one device — a phone and a tablet, an old
phone and a new one — you know the small frustration of setting everything up
again. You carefully install your Bibles, commentaries, dictionaries and other
documents on one device, then have to hunt them all down again on the next.

**Document Sync** takes care of that for you. Turn it on, and the documents you
install on one device are copied to your own cloud storage and re-installed on
your other devices automatically. Set up a new device, enable sync, and your
whole library comes down with it.

## What gets synced

Everything you’ve installed, regardless of how you installed it:

- **Bibles, commentaries, dictionaries, maps and other books** from the
  download list
- **Sideloaded documents** — modules you added from a ZIP file
- **MyBible, MySword, eSword and EPUB** documents

Because AndBible copies the actual document — not just a “go download this
again” note — it works even for sideloaded modules and documents from custom
repositories that can’t simply be re-downloaded elsewhere.

## It uses your own cloud account

Document Sync uses the **same cloud connection as the rest of AndBible’s sync**
— your own Google Drive or Nextcloud account. Your documents are stored in your
private cloud space, not on anyone else’s server.

## Turning it on

Open **Settings → Synchronization** and switch on **Documents** (it sits in the
list of sync categories alongside Bookmarks, Workspaces and the rest). After you
sign in, AndBible shows you exactly what’s about to happen — for example,
“Upload 12 (45 MB) · Download 3 (8 MB)” — so there are no surprises before
anything transfers. Confirm, and syncing begins in the background.

From then on it’s automatic: install a document on one device and it shows up on
the others; upgrade a document and the newer version follows. Transfers run
quietly in the background with a notification, so they never hold up what you’re
doing.

## The Synced Documents screen

**Settings → Synchronization → Synced documents** gives you one place to see and
manage everything — both what’s installed on this device and what’s in your
cloud. Each document shows its status: *Synced*, *On this device only*, *In
cloud only*, *Update available*, or *Blocked*.

From here you can:

- **Download** a document that’s in the cloud but not yet on this device
- **Push** a document that’s only on this device up to the cloud
- **Block a document from this device** — handy when you don’t want a large
  document taking up space on, say, an older phone, while keeping it synced on
  your others
- **Remove a document** from the cloud (and, when sync is on, from all your
  devices)
- **Sync now** — a manual one-time sync, useful if you prefer to keep automatic
  sync off and pull things over by hand

You can also pull down on the list to refresh it.

## On your terms

- **Wi-Fi only (default):** automatic transfers wait for an unmetered connection,
  so Document Sync won’t eat into your mobile data. Manual actions always go
  through immediately when you ask for them.
- **Per-device choices:** blocking a document only affects the device you block
  it on; your other devices keep syncing it.
- **Manual mode:** you don’t have to enable automatic sync at all. As long as
  you’re signed in to your cloud account, you can open Synced Documents and move
  documents to and from the cloud by hand whenever you like.

## A note on removing documents

Removing a document behaves the way you’d expect for each situation:

- With **automatic sync on**, *Remove from all devices* removes it from the
  cloud and from your devices — including the one you’re holding — so you don’t
  end up with a stray copy that’s no longer being synced.
- With **automatic sync off**, *Remove from cloud* clears it from your cloud
  storage but leaves the copy on this device untouched.
