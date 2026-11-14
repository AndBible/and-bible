# Bible Links / Deep Linking

*AndBible* (since version 5.0) supports deep linking via the following URL format:

`https://read.andbible.org/OSISREF`

where `OSISREF` is a standard OSIS reference to a Bible verse, such as `Gen.1.1`.

`read.andbible.org` links open in the app on Android only; on other platforms they open in the browser (see [Platform differences](platform_differences.md)).

Opening a link from another app navigates the active window to that passage without
creating another window. This works both when *AndBible* is closed and when it is
already running. Without a `document` parameter, the active window's remembered
Bible is used (or the default Bible if the window has no Bible yet).
The Links window preference still applies to links followed inside *AndBible*.

A full list of OSIS book abbreviations can be found in the
[OSIS User Manual (pages 123-124)](https://www.crosswire.org/osis/OSIS%202.1.1%20User%20Manual%2006March2006.pdf).

## Query Parameters

You can use the following optional query parameters:

- `document` — open the link in a specified document (if installed).
- `v11n` — tell *AndBible* that the link uses a specific versification.
- `ordinal` — the position inside the document that the link points to. Copied links add
    it automatically when needed; you normally do not write it yourself.

For dictionaries and other keyed documents the key can also be written with the document name,
for example `StrongsGreek:5548`.

## Copying Links

*AndBible* allows copying a link to the current page via the window menu: long-press the
window button at the bottom of the screen (right-click it on desktop), or tap the button at
the top right of the window, and select `Copy link to clipboard`. The item is not offered
for some special documents (those that are not a normal Bible, book or dictionary page).

## Examples

- <https://read.andbible.org/1Sam.1.2?document=FinRK&v11n=KJV>
- <https://read.andbible.org/1Sam.1.2?document=FinRK>
- <https://read.andbible.org/1Sam.1.2?v11n=KJV>
- <https://read.andbible.org/1Sam.1.2-1Sam1.5?v11n=KJV>

## Other Link Formats {#alternative-link-formats-non-google-builds-only}

Only `https://read.andbible.org/` links are registered with Android as links that open in
*AndBible*. The app’s code can read StepBible.org and Bible.com addresses, but no link
registration exists for them, so tapping such a link in another app does not open
*AndBible*. Use `read.andbible.org` links instead.

## External Links in Document Text

When you tap an external web link in Bible text, a commentary, or another document,
*AndBible* asks for confirmation before opening it in your browser. This applies
on Android, iOS, and desktop. Choose **OK** to open the link or **Cancel** to stay
in *AndBible* without opening it.

Other web links, including help and homepage links in the app's interface, keep
their usual behavior; discrete mode still asks before opening external links.

## Enabling the Feature

Enable the feature via `Application preferences` > `Advanced settings` > `Open Bible links in AndBible`.
The row is shown on Android 12 and later only. It opens Android’s *Open by default* page for
*AndBible*, where the `read.andbible.org` link must be allowed to open in the app.
