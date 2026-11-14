# Backup and Restore

## Overview

The backup and restore function allows you to export your *AndBible* data so that
you can restore it on other devices and to preserve your data in
case of device failure. It is recommended to backup your database file
often. The database contains your notes, bookmarks, application settings,
reading plans, and other information.

## Damaged app databases

Before opening an app database, AndBible checks that its schema can be read.
If SQLite reports corruption, AndBible closes that connection and checks again
with a new connection. A single failed check, a lock, or an I/O error does not
cause the database to be replaced.

If both checks report corruption, AndBible moves the damaged database and any
remaining journal or lock files aside under a name containing `.corrupt-` and
a timestamp, then creates an empty replacement. The saved files are not restored
automatically. Restore a known-good backup to recover your data. This automatic
recovery does not apply to files selected for import or restore.

## Recovery after repeated crashes on iOS

If AndBible detects repeated crashes within an hour, it offers a recovery screen
before opening its databases. If calculator protection is enabled, unlock it first.
Choose **Backup & Restore** to access recovery tools without starting the reading
workspace, or **Continue** to try normal startup again. The back button in recovery
Backup & Restore returns to the recovery screen.

Backgrounding the app does not count as a crash. The recovery screen itself is not
counted as another failed startup if the system closes the app while it is shown.

## How to backup your data

Opening and sharing files differs between platforms; see [Files and sharing](platform_differences.md#files-and-sharing).

1. From the main menu tap *Backup & Restore*.
2. Under *Backup* switch on the kinds of data you would like to back up. The
    switches are independent: you can switch on several at once, and
    *Backup to…* then backs each one up in turn.

    > - *Database* backs up your Application Settings, Bookmarks, Labels,
    >     Notes, Study Pads, Reading Plans, Workspaces, etc.
    > - *Documents* backs up your Bibles, Dictionaries,
    >     Commentaries, Maps, etc. (You will be prompted which modules you
    >     want to backup)
    > - *Application* backs up the .apk file (Android only; the switch is not shown on iOS and desktop)
3. Tap *Backup to…*.
4. Choose where you would like to send the backup:

    - *Phone Storage* (Android; you will be prompted to choose a folder on your device)
    - *Save to Files* (iPhone, iPad and Mac; you will be prompted to choose a place in the Files app)
    - *Share* (you will be able to choose from a list of installed applications)

    On Windows and Linux there is no such question: a save dialog opens straight away,
    and you choose the folder and file name there.

Document backups store each file path only once, even when selected modules share
files. If two installed versions provide the same path, the first selected version's
file is kept in the backup.

## How to restore your data

NOTE: If your backups were saved to cloud storage, download the backups to your
device before proceeding.

On the welcome screen (the *Advanced* tab) you can also choose *Restore Database File*. If the selected
file cannot be read or the backup is damaged, an error dialog appears. Dismiss
it to stay on the welcome screen and try again with a readable backup file.

You can cancel before database replacement begins. Once replacement starts,
AndBible finishes the selected restores and imports and clears the restored
sync configuration, even if the screen is closed. This protects against a
partly completed restore; it does not protect against the app process being
stopped or the device losing power.

To restore your *AndBible* data:

1. From the main menu, tap *Backup & Restore*.
2. Under *Restore or Import* switch on the kind of data you would like to
    restore (the switches are independent, so you can switch on both):

    - *Database* restores your Application Settings, Bookmarks, Labels,
        Notes, Study Pads, Reading Plans, Workspaces, etc.
    - *Documents* restores your Bibles, Dictionaries, Commentaries,
        Maps, etc.
3. Tap *Restore or Import from…*.
4. For *Documents*, if prompted, tap *Proceed* on the next dialog which indicates
    supported filetypes.
5. Select the zipped backup file on your device to restore your *AndBible* data.
6. For *Database*, AndBible asks which backup sections you want to restore. For
    each section you can choose *Restore* (the existing data is removed and the
    data from the backup is loaded) or *Import* (the data from the backup is
    merged into your existing data).

!!! note

    Since version 5.0, you can choose exactly what to restore from a database
    backup — for example, only bookmarks or only workspaces — rather than
    restoring everything at once.

When upgrading or restoring a database from before version 5.0, AndBible migrates
it to the current database format. If an old reading-plan table is missing or
lacks its required ID column, that table is skipped so the rest of your data can
still be migrated. Reading plans or reading progress from the skipped table
will not be available after migration.

For a legacy version-10 database with missing workspace tables, AndBible recreates
those tables empty before continuing the upgrade. Existing tables and their data
are preserved, but data from a missing table cannot be recovered this way.

## Auto-Backups, Reset Databases and Last crash info

Below the restore buttons the *Backup & Restore* screen may show more sections:

- **Auto-Backups** lists the database backups AndBible has written automatically
    (for example when it opens a database saved in an older format). Each entry
    shows its date and the app version, with two buttons: *Export*, which saves
    the file elsewhere, and *Restore*, which restores from that file.
- **Reset Databases** has a button for each database that can be reset to its
    initial empty state. This cannot be undone, so make a backup first.
- **Last crash info** shows the details of the app's last crash, which is useful
    when reporting a bug.

To restore you *AndBible* apk file:

1. Locate the .apk file you previously backed up.
2. Tap on the .apk file to install the *AndBible* application.

    - Note: You may need to give permission to allow your file manager
        application to install .apk files.
