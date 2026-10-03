# Bible Links / Deep Linking

*AndBible* (since version 5.0) supports deep linking via the following URL format:

`https://read.andbible.org/OSISREF`

where `OSISREF` is a standard OSIS reference to a Bible verse, such as `Gen.1.1`.

A full list of OSIS book abbreviations can be found in the
[OSIS User Manual (pages 123-124)](https://www.crosswire.org/osis/OSIS%202.1.1%20User%20Manual%2006March2006.pdf).

## Query Parameters

You can use the following optional query parameters:

- `document` — open the link in a specified document (if installed).
- `v11n` — tell *AndBible* that the link uses a specific versification.

## Copying Links

*AndBible* allows copying a link to the current page via the window button menu:
tap the window menu and select `Copy link to clipboard`.

## Examples

- <https://read.andbible.org/1Sam.1.2?document=FinRK&v11n=KJV>
- <https://read.andbible.org/1Sam.1.2?document=FinRK>
- <https://read.andbible.org/1Sam.1.2?v11n=KJV>
- <https://read.andbible.org/1Sam.1.2-1Sam1.5?v11n=KJV>

## Alternative Link Formats (non-Google builds only)

In non-Google builds, *AndBible* also supports StepBible.org links:

<https://stepbible.org?q=reference=Gen.1.1|version=FinRK&v11n=KJV>

The `v11n` parameter is not supported by StepBible but can be used when
targeting *AndBible*. The `version` (document) parameter is supported by StepBible
if their version name matches the *AndBible* document name.

Bible.com links are also supported:

<https://www.bible.com/fi/bible/1745/1SA.13.FINRK>

## Enabling the Feature

Enable the feature via `Application Preferences > Open Bible links in AndBible`.
