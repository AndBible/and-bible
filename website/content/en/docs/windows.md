# Windows

<span id="table-of-contents"></span><span id="id1"></span><span id="id2"></span>

The ‘Bible Study App’ provides amazing window functionality.

- Open as many windows as you need, showing ‘any’ document you want.
- Show your ‘preferred’ bible, a translation with ‘Strongs’ numbers or ‘another’ language translation. You can also show cross-references, commentaries and dictionaries. You can ‘even’ show maps.
- Each window has its own button at the bottom of the screen. Use it to show or hide that window. Or long press the button to access the window menu.
- You can also access the window menu from the button at the top right of each window. The appearance of each window can be configured from the `Text options` item of that menu.
- Windows can be synchronised so they always show the same verse, with the `Synchronise` item of the window menu (see [Synchronising windows](#synchronising-windows)).
- There are three types of windows: pinned windows, unpinned windows and the links window. Window pinning is on by default; see [Window pinning](#window-pinning).
- With window pinning on, any number of pinned windows can be shown at one time, but only one unpinned window.
- Document links are opened in the links window (the `Links window` setting is on by default).

On desktop, click inside a reading window to make it active. The toolbar then
controls that window, just as tapping a reading window does on Android. When you
change a window's document, its window-button label updates without needing to
activate a different window first.

When you scroll a synchronised commentary window, its title follows the verse
range of the entry in view, including when you return to an already-loaded entry.
The synchronised Bible windows follow that entry, too.

When a links window shows several references together, it remembers the fragment
and verse or paragraph you scrolled to. Closing and restarting the app restores
that reading position instead of returning to the first reference.

https://www.youtube.com/watch?v=mKxEuQX-oX0

## Creating and configuring windows

Multiple windows makes it easy to compare and drill down into verses.

There are three ways to create a new window.

1. First, push the `+` button in the bottom right corner. It is shown only while exactly one window is open. With several windows the same place holds an expand/collapse chevron for the window-button bar, and with a maximised window it is `Restore all windows`.
2. Second, choose `New window` via the window button at the top right of any window.
3. Third, long-press a window button at the bottom of the screen and choose `New window`.
   On desktop, you can also right-click a window button to open its menu.

A new window starts with the source window's document and reading position, but
inherits its text display settings from the workspace defaults. Window-specific
changes, such as a different font size or background color, are not copied.
Later changes to workspace settings also apply to the new window unless you
override them for that window.

On iOS, a newly opened reading window uses its page background color while the
text loads, including in split views and after switching workspaces. The background
follows the current day or night colors; monochrome mode uses white by day and
black at night.

There are multiple ways to choose the document you want to show in the new window.

1. You can choose a Bible by clicking the Bible-button at the top.
2. Or you can choose a commentary by clicking the Commentary-button.
3. Or you can choose any document from the application menu.

A window can be maximised (`Maximise` in the window menu) so it takes up the whole screen. Restore it to its original size by pushing the `Restore all windows` button in the bottom right of the screen.

When you open a link (a cross reference, Strong's, Compare, Memorize, My Notes, a StudyPad, a search result) in a maximised window and the `Links window` setting is on, the link opens in the links window, which is shown maximised in its place. Back, closing the links window or the `Restore all windows` button returns to the window that was maximised before, at the same place. If you moved on inside the links window (for example opened another passage there), Back first steps back through those passages. After the app is restarted, a maximised links window is shown in the normal split view. With the setting off, links open in the maximised window itself.

A window can be minimised (`Minimise` in the window menu) so that it is hidden from view. Show the window again by clicking it’s button in the window-button-bar at the bottom of the screen.
Minimised windows have a dashed outline around their bottom-bar button. In true
monochrome mode, their text and outline stay black on white (or white on black in
the dark theme), rather than fading to gray. Bottom-bar buttons have square bottom
corners; pinned and links windows have more rounded top corners than unpinned
windows. While `Window pinning` is unticked, all window buttons use the pinned shape.

The expanded window-button bar spans the full width of the reading area, with
its buttons aligned to the end. In a side-by-side split, every window reserves
the same bottom strip so the buttons do not cover text. In a stacked split, only
the lowest window reserves it. Collapsing or hiding the bar releases the strip
in every window; the small expand, new-window or restore control does not reserve
a strip. Navigation-bar and taskbar space is still kept clear in every bottom
window.

Swipe down on the button at the top right of a window, or long-press it, to
minimise it. If you open a link inside the links window, swiping down on (or
long-pressing) the new links window's button closes that window instead, just
like tapping its bottom-bar button. The first links window, and links windows
with another links window opened from them, are minimised rather than closed.
The `Minimise` item in the window menu always only minimises.

Reposition the window by using the `Move to` menu option.

## The window menu

Long-press a window button at the bottom of the screen, or tap the button at the top right of a window, to open its menu. Which items you see depends on the window and its document:

- `New window`, `Maximise` and `Minimise`: see above.
- `Change to normal window`: shown for the links window; turns it into an ordinary window.
- `Move to`: a list of the positions of the other windows of the same kind (pinned or unpinned).
- `Pin`: see [Window pinning](#window-pinning). Shown only while `Window pinning` is on.
- `Synchronise`: see [Synchronising windows](#synchronising-windows). Shown only for windows that can be synchronised.
- `AI Actions`: shown when an AI provider is set up. See [AI](ai.md).
- `Add bookmark for whole page`: for documents that are not Bibles, such as a commentary or book page, bookmarks the whole page.
- `Export as HTML`, `Export Study Pad` and `Export Bookmarks as CSV`: shown for a Study Pad (the HTML export also for My Notes and multi-reference windows). See [Sharing](share.md) for what they produce.
- `Text options`: a submenu with the most recently used settings, `All text options`, and `Copy settings to` (see below).
- `Copy link to clipboard`: copies a link to the current passage.
- `Open …` (headset icon): shown while speech is active; goes to the passage being spoken. See [Speak](speak.md). A second `Open …` item with the reference appears when you have copied one to the clipboard.
- `Close`: closes the window.

### Copy settings to

In the `Text options` submenu, `Copy settings to` copies this window's text display settings to another window of the workspace (`Window N`), to the whole `Workspace`, or to `Global defaults`.

### Window pinning

`Window pinning` is a toggle in the three-dot menu (⋮) at the top right of the screen. It is on by default, so each ordinary window has a `Pin` item in its menu. Untick it to make every window act as pinned and hide the `Pin` item:

- Pinned windows are displayed first. You can show any number of them.
- Only one unpinned window is shown at once. Unpinned windows suit commentaries, maps and other extra material, while your Bibles stay pinned.

### Synchronising windows

`Synchronise` in the window menu is a submenu with `Group 1` to `Group 6` and, once the window is in a group, `Disable synchronize`. Windows in the same group always show the same verse. A window can be in one group at a time.

### Other options in the three-dot menu

These toggles in the ⋮ menu affect all windows of the workspace:

- `Reversed split mode`: normally windows are side by side in landscape and stacked in portrait; this flips that. Shown only while more than one window is open.
- `Tilt to scroll`: scrolls the text by tilting the device. Shown only on devices that have a motion sensor, so not on desktop.
- `Window pinning`: see above.
- `Workspaces` opens the [workspace selector](workspaces.md), and `Label settings` opens the [label settings](labels.md) for the workspace.

Several other items of this menu are described in [Navigation](navigation.md).

A window has many customisation settings. The most recently used settings appear in the `Text options` submenu. But you can access all window settings via the `All text options` menu item.

Text display settings follow a three-level hierarchy: **global defaults →
workspace settings → window settings**. Each level inherits from the one above
it. For a full explanation of how this works, see [Text display settings hierarchy](look_and_feel.md#settings-hierarchy).

Here is a quick summary of the difference between the levels:

- **Global settings** set the defaults for all workspaces and windows. Access
    them via the link at the top of any workspace or window text options screen.
- **Workspace settings** override global defaults for one workspace and all its
    windows. Access them via the dot menu at the top right of the screen.
- **Window settings** override the workspace setting for one specific window.
    Access them via the window buttons. A setting inherited from a higher level
    shows a small text label, `Workspace` or `Global`, naming the level it comes
    from. A setting with no label has been changed at this level.

So, if you want to change the appearance of all windows everywhere, change the
global setting. If you want all windows in one workspace to look different,
change the workspace setting. If you only want one window to look different,
change the window setting.

Some examples:

- I want the cross-reference window only to have a different background color.
    So I change the window setting.
- I want all other windows to have a different background color. So I change
    the workspace setting. This won’t affect the window I just changed because
    setting the color at the window level overrides the workspace setting.
- I want all windows everywhere to have a smaller font size. So I change the
    global setting. This affects every workspace and window that has not been
    individually overridden.

https://www.youtube.com/watch?v=N7awlmZXFdQ
