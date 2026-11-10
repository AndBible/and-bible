# Navigation

## Opening Bible References

To open a bible reference, simply click on the book name at the top of the screen:

[![AndBible Header Reference](images/header-reference.png){ height="100" }](images/header-reference.png)

The *Book Chooser* will open where you can select any book that is available in
the current document. For example, in regular Bible documents you will be able
to see all 66 books of the Bible, but if you go to the *Book Chooser* when the
active window is a smaller commentary, it will only show the books that are
available in that commentary.

After you select the book, it will take you to the next screen where you can
select the chapter:

[![AndBible Chapter Chooser](images/book-chapter-chooser.png){ width="200" }](images/book-chapter-chooser.png)

After selecting a book and then a chapter it will take you back to the Bible
screen and open the Bible to the selected reference.

### Book Chooser Customizations

A few customization options are available in the top right vertical three-dot menu
of the *Book Chooser* screen:

> - **Alphabetical Order:** Enabling this option will display the books in
>     alphabetical order. (Starting with numbers - i.e. 1 Chronicles)
> - **Order books horizontally:** Enabling this option will list the books from
>     left to right then from top to bottom.
> - **Group books by category:** Enabling this option will automatically enable
>     the *Order books horizontally* setting but will additionally group books by
>     their category (i.e. Law, History, Poetry, Prophets, etc.)
> - **Show long book name:** Enabling this option will additionally show the full
>     book name under its abbreviation.

### Opening Deuterocanonical Books

When your active window is a Bible version that contains deuterocanonical books,
there will be a plus (+) icon in the *Book Chooser*. By clicking on the plus (*+*)
icon, you will see a list of the included deuterocanonical books. For example,
when opening the KJVA Bible, you can click the “Book Chooser” and you should
see the plus (+) menu icon in the top right. Clicking the plus icon (+) will
display the deuterocanonical books in the list:

[![AndBible Chapter Chooser](images/book-chooser-deuterocanonical.png){ width="200" }](images/book-chooser-deuterocanonical.png)

### Navigating Directly To Verse

If you would like to choose a specific verse number (in addition to the book and
chapter), you can enable the *Navigate to verse* option in Application preferences:

1. Click on the main menu (☰) on top left of the screen,
2. Click *Application preferences*
3. Find and enable the *Navigate to verse* setting.

Now after selecting the chapter number, you will be prompted to choose the exact
verse number you would like to open.

## Moving Between Documents

### Opening Documents

To open a document, click on either the Bible or Books icons in the top navigation bar.

[![Book chooser icons](images/book-chooser-icons.png){ height="100" }](images/book-chooser-icons.png)
> - Clicking on the Bible icon will show a list of installed Bibles that you can open.
> - Clicking on the Books icon will show a list of other installed documents you can open (
>     i.e. Commentaries, Dictionaries, Generic books, etc).

By default each button opens a sheet. The Bible button lists your Bibles. The
other button is titled *Commentary* and lists commentaries, general books and
dictionaries. The tabs are *Recent*, *This verse* and *All*, and *All
documents…* at the bottom opens the full document list. If only two Bibles (or
books) are available for the current verse, the button switches directly to the
other one instead; if none are, it opens the full document list.

Note: This will open the document in the currently active window. To open a
document in a new window, see [Windows](windows.md).

The Bible and books toolbar buttons can also be customized. To customize the
actions, to to the top left main menu (☰) and click *Application
Preferences*. Click *Action for toolbar button press* to configure the custom
button actions:

> - Press to open menu, long press for documents screen (default)
> - Press to open next document, long press to open the document sheet (list of documents)
> - Press to open next document, long press to open documents screen

### Closing Documents

To close a document, click the ☰ menu at the top right of the document
you want to close and click *Close*. Alternatively, long-click on a bottom-right
window bar button and click *Close*.

### Maximizing/Minimizing Document Windows {#maximizing-minimizing-document-windows}

To maximize or minimize a window, click on the window icon in the bottom right
window button bar. Clicking on a window that is visible will minimize the window
and clicking on a window that is minimized will maximize it. Maximized window
buttons will have a grey background while minimized window buttons will have a
darker background. The window button that is highlighted indicates the active
window (which should also be maximized):

[![Open the workspaces dialog](images/window-buttons.png){ height="100" }](images/window-buttons.png)

For more on managing windows, see [Windows](windows.md).

## Moving Between Workspaces

### Open the workspaces dialog

To open the workspace dialog, simply swipe down from the book reference section
of the top main toolbar. Alternatively, click the top right three-dot menu and
click *Workspaces*.

[![Open the workspaces dialog](images/workspace-dialog-open.gif){ height="400" }](images/workspace-dialog-open.gif)

### Go to the next workspace

To navigate directly to the next/previous workspace, you can swipe right or left
on the book reference section of the top main toolbar:

[![Open the workspaces dialog](images/workspace-move-to-next.gif){ height="400" }](images/workspace-move-to-next.gif)

For more information about managing workspaces, see [Workspaces](workspaces.md).

## Background activity

When sync is configured, **Sync now** is the first row of this section.

On desktop and iOS (including iOS on a Mac) the top right three-dot menu (⋮)
can show what the app is doing in the background, such as downloads, search
indexing and sync. On Android the same information is shown as system
notifications, so this is off by default there. Turn it on or off with
*Show background activity in the menu* in the *Look & feel* section of the
application settings (see [Look and Feel](look_and_feel.md#look-feel)).

When the setting is on:

- While a download or install, search indexing or sync is running, a thin
    progress ring is drawn around the ⋮ button. The ring shows real progress
    when exactly one item reports a percentage.
- A failure shows a small count badge on the ⋮ button. The small sync icon next
    to the title is not shown, because the ring already covers sync.
- Opening the ⋮ menu shows an activity section at the top with up to three
    rows: items needing attention first, then other running items and failures.
    Each row has a progress bar or a message. Opening the menu marks failures as
    read, but does not clear a permission-waiting agent's attention badge; that
    badge remains until the permission request is resolved.
- *Show all (N)* opens the *Background activity* list, with a *Running* and a
    *Recent* section. Running items that can be stopped have *Cancel*, finished
    items have *Dismiss*, and *Clear
    finished* removes all finished items. Tap a download row to open the download
    screen, or a sync failure to open the sync settings. On wide windows the list
    is a dialog, on phones a bottom sheet.
- An AI-agent run appears as one activity for its workspace. Its title includes
    the workspace name when available, and otherwise uses the generic *AI agent*
    title. While it runs, the row can show its latest log message; if it is
    waiting for your permission, the row says which tool needs approval and is
    kept at the top of the menu activity section as an item needing attention.
    Tapping the row switches to that workspace and opens the agent log. *Cancel*
    stops only that run. A failed run stays in the list until dismissed or the app
    restarts; opening the menu marks it as read. A completed run stays for one
    minute, and a cancelled run is removed.
- On desktop and iOS, a background sync error appears as a short message
    ("... see menu") and a row in the list instead of an error dialog.

Reportable failed rows also have a *Report* action, which opens the existing
bug-report flow with the failure attached; reporting does not dismiss the row.
Expected sync errors, such as authentication or quota errors, do not offer
*Report*.

Successful items stay in the list for one minute, and a successful sync is not
listed. Failures stay until you dismiss them or restart the app. Very short
jobs (under a second) are not shown.

## Gestures and Shortcuts

*AndBible* supports a variety of gestures and shortcuts for efficient navigation.

### Keyboard Search

With the reading view open, **Ctrl+F** opens the in-place search field, including
on iPhone and iPad with a connected keyboard. You can also open the same search
from **Find** in the main menu. These are **Control** shortcuts, not Command
shortcuts on an Apple keyboard. While a native text field has focus, its own
keyboard commands take precedence over reading shortcuts. In the Bible text's
note editor, **Ctrl+C** copies and **Ctrl+B** stays with the editor. Alt+arrow
keys and Alt+M/O do not trigger reading actions while editing, on Android and
other platforms as well as iOS. On iOS, the editor also owns Control shortcuts
such as Ctrl+B and Ctrl+F for caret navigation, and Option+arrow navigation.

### Esc Key as Back {#esc-key-as-back}

With a hardware keyboard on iPhone, iPad, the iOS app on a Mac, and the
desktop app, **Esc** works like the Back button. Each press closes the topmost
open thing first: a dialog, popup or sheet, then the main menu, then the search
results and finally **Find** (the search field in the toolbar). On a screen
opened over the Bible text, such as the settings or the Bible chooser, Esc
returns to the previous screen. On the desktop reading screen, Esc also exits
fullscreen; otherwise, with nothing open, it does nothing.

On iPhone, iPad and the iOS app on a Mac, while you type in a text field, the
first Esc only leaves the field (and hides the on-screen keyboard); press Esc
again to go back. Esc that a note editor or an input method uses itself, for
example to cancel a composition, never also counts as Back.

### Swipe Left/Right on Bible Text {#swipe-left-right-on-bible-text}

You can swipe left or right on the Bible text to navigate. The behavior can be
configured in `Settings` > `Application behavior` > `Action for swipe
left/right gesture`:

- **Chapter** (default) – Navigate to the next or previous chapter.
- **Page** – Scroll by one page (like tapping the margin in the Android or iOS app).
- **Disabled** – Swiping has no effect on the Bible text.

### Double-Tap to Fullscreen

Double-tap the Bible text to enter fullscreen mode, which hides the toolbar and
status bar for distraction-free reading. Double-tap again to exit fullscreen.

This feature is enabled by default on Android, iPhone, and iPad. It is disabled
by default in the desktop app and when running the iPad app on a Mac. You can
change it in `Settings` > `Application behavior` > `Double-tap to fullscreen`.
Your saved choice is kept.

On desktop, choose **Fullscreen** in the main menu or press **F11** to enter
fullscreen. Click the exit-fullscreen button at the upper right, or press **Esc**
or **F11**, to exit it independently of the double-tap setting. When a dialog or page editor is open, close it first.

### Fullscreen by Scrolling

When enabled, the app automatically enters fullscreen mode when you start
scrolling the text, and exits fullscreen when you scroll back to the top.

To enable: `Settings` > `Application behavior` > `Fullscreen by
scrolling`.

### Volume Keys Scrolling

Volume-key scrolling works on Android only; see [Platform differences](platform_differences.md).

By default, the device volume buttons scroll the Bible text up and down instead
of adjusting the system volume. This is useful for hands-free reading.

To disable: `Settings` > `Application behavior` > `Volume keys scroll`.

### Long-Press Actions

Several buttons support long-press for quick access to additional features:

- **Speak toolbar button** – Open speak settings directly.
- **Window button** (in the bottom bar) – Minimize the window instantly.
- **Verse location title** (top of screen) – Jump to the document selector.

The navigation history list is in the main menu as `History`.
