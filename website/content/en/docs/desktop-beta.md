# Desktop beta: known limitations

The desktop version of *AndBible* is in beta. Reading, studying and syncing work, but some things are
missing and some environments have not been tried yet. This page lists them so you know what to expect.
For installing and everyday use, see the desktop page of the documentation (AndBible on the desktop).

## What has been tried

On Linux the beta was tried by hand on the installed `.deb` package: installing modules from the
catalogue, reading and navigating, selecting text (copy, bookmark, note), StudyPads, search, several windows,
workspaces, exporting and importing a backup, opening a backup file from the file manager, night mode,
text options, the Finnish and Hebrew (right-to-left) interface, and closing and reopening the app with
your position, windows and settings kept.

## Not tested yet

These setups may work, but they were not checked for the beta:

- **Wayland-native sessions.** On Wayland the app runs through XWayland.
- **GNOME and KDE specifics.** For example how file dialogs, window decorations and the system tray look and behave.
- **Input methods (IME), including Chinese, Japanese and Korean input.**
- **Fractional display scaling** (for example 125% or 150%).
- **Touchpads.** Smooth and horizontal scrolling and pinch gestures.

If you use one of these and something looks wrong, please [report it](#reporting-a-bug).

## Missing features

- Printing.
- Find in the current page. Use the search in the app to search in a Bible instead.
- Desktop notifications.
- Speak (text to speech).
- Images and styles of EPUB books are not shown.
- Fonts that come with some modules are not used.
- The app does not follow the dark mode of your operating system. Choose *Manual* in
  *Application preferences* under *Night mode switching*, and use the *Night mode* menu item to switch.

## Things to be aware of

- **Close the window normally.** The app saves your reading position when you close it. If the app is
  killed (for example with the task manager or a power cut), the reading position since the previous save
  can be lost.
- **Phone wording.** Some screens still use words from the phone app. For example *Phone storage* and
  *Application (.APK)* in *Backup & Restore*, and *Keep screen on* and *Volume buttons scroll* in the
  preferences. These options do nothing on desktop.

## Windows notes

- The *AltGr* key was checked on the Finnish keyboard layout. Other non-US layouts were not checked, so
  some keyboard shortcuts may not match your keyboard.
- The beta packages are not signed, so Windows may show a SmartScreen warning when you install.

## Reporting a bug

In the app open *Help & tips*, then *Feedback / bug report*, and choose the bug report. Please
tell us your operating system and version, whether you use Wayland or X11 on Linux, and what you were
doing when the problem happened.
