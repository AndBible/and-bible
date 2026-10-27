# Backup and Restore

## Overview

The backup and restore function allows you to export your *AndBible* data so that
you can restore it on other devices and to preserve your data in
case of device failure. It is recommended to backup your database file
often. The database contains your notes, bookmarks, application settings,
reading plans, and other information.

## Recovery after repeated crashes on iOS

If AndBible detects repeated crashes within an hour, it offers a recovery screen
before opening its databases. If calculator protection is enabled, unlock it first.
Choose **Backup & Restore** to access recovery tools without starting the reading
workspace, or **Continue** to try normal startup again. The back button in recovery
Backup & Restore returns to the recovery screen.

Backgrounding the app does not count as a crash. The recovery screen itself is not
counted as another failed startup if the system closes the app while it is shown.

## How to backup your data

1. From the main menu tap *Backup & Restore*.
2. Under *Backup* select the radio option for the type of data you would like
    to backup:

    > - Choose *Database* to backup your Application Settings, Bookmarks, Labels,
    >     Notes, Study Pads, Reading Plans, Workspaces, etc.
    > - Choose *Documents* to backup your Bibles, Dictionaries,
    >     Commentaries, Maps, etc. (You will be prompted which modules you
    >     want to backup)
    > - Choose *Application* to backup the .apk file
3. Tap *Backup To…*.
4. Choose where you would like to send the backup:

    - *Phone Storage* (you will be prompted to choose a folder on your device)
    - *Share* (you will be able to choose from a list of installed applications)

Document backups store each file path only once, even when selected modules share
files. If two installed versions provide the same path, the first selected version's
file is kept in the backup.

## How to restore your data

NOTE: If your backups were saved to cloud storage, download the backups to your
device before proceeding.

On the welcome screen, you can also choose *Restore database*. If the selected
file cannot be read or the backup is damaged, an error dialog appears. Dismiss
it to stay on the welcome screen and try again with a readable backup file.

To restore your *AndBible* data:

1. From the main menu, tap *Backup & Restore*.
2. Under *Restore or Import* select the radio option for the type of data
    you would like to restore:

    - Choose *Database* to restore your Application Settings, Bookmarks, Labels,
        Notes, Study Pads, Reading Plans, Workspaces, etc.
    - Choose *Documents* to restore your Bibles, Dictionaries, Commentaries,
        Maps, etc.
3. Tap *Restore or Import From…*.
4. If prompted, tap *Proceed* on the next dialog which indicates supported
    filetypes.
5. Select the zipped backup file on your device to restore your *AndBible* data.

!!! note

    Since version 5.0, you can choose exactly what to restore from a database
    backup — for example, only bookmarks or only workspaces — rather than
    restoring everything at once.

When upgrading or restoring a database from before version 5.0, AndBible migrates
it to the current database format. If an old reading-plan table is missing or
lacks its required ID column, that table is skipped so the rest of your data can
still be migrated. Reading plans or reading progress from the skipped table
will not be available after migration.

To restore you *AndBible* apk file:

1. Locate the .apk file you previously backed up.
2. Tap on the .apk file to install the *AndBible* application.

    - Note: You may need to give permission to allow your file manager
        application to install .apk files.
