# Discrete Build

The discrete version of *AndBible* is meant to be used in environments where Bible
applications are not tolerated. It appears as a calculator application.

## Usage

With the discrete version, the user first sees a calculator application where
it is possible to do basic calculations. To enter *AndBible*, type the secret PIN
code and press `=`.

The default PIN is **1234**.

Once the app is set up, the PIN can be changed in
`Application Preferences > Calculator PIN`.

## iOS

On iOS, discrete mode uses a calculator icon for the app. iOS shows a system
confirmation when the icon changes; this confirmation cannot be hidden. Turning
discrete mode off restores the primary icon. The app name remains AndBible: the
calculator gate hides content before PIN unlock, but does not hide the app's
identity from iOS or from someone inspecting the device.

While discrete mode is on, new spoken-passage updates show the localized
calculator title on system media controls, without the book, passage or artwork.
Changing the mode during speech takes effect on the next passage update.
Notification permission requests are skipped in discrete mode because the system
prompt names the app. In-app error dialogs still show their messages; they are
not system notifications.

## Download

The discrete build is available from the
[GitHub releases page](https://github.com/AndBible/and-bible/releases/latest).

Click the “Assets” drop-down and download the file named
`calculator-production-###.apk` (where `###` is the version number).
