# Windows Store listing draft

`listing.csv` is derived from the Partner Center export
`listingData-9MZ52K1LQRBZ-1152921505702421518.csv` for Store ID `9MZ52K1LQRBZ`.
It preserves the exported rows and uses an explicit `en-us` column; `default`
is intentionally empty. English is the only authored Store locale. Other app UI
languages remain usable. Future translations should add locale columns based on
the reviewed English text.

This is a text draft, not a complete submission. In Partner Center, use
**Store listings -> Import listings -> Import .csv** to import the text. Once
screenshots exist, use folder import for the listing and its assets. A full
listing needs at least one genuine Windows screenshot; do not substitute Android
screenshots. First publication still takes place in Partner Center. Import and
acceptance have not been verified.

Before import, validate the CSV with a standard-library CSV reader: check the
header and row widths, exported Field/ID/Type values and order, unique nonempty
IDs, empty default cells, text limits, and intentionally empty release notes,
asset paths and hardware fields. Check the description survives CSV roundtrip
with its paragraphs intact.

Screenshots, MSIX changes, credentials and publication are outside this commit.
Translations, packaging verification and the privacy-policy update for optional
AI/provider data handling remain future work.
