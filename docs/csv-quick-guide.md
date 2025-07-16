# Bookmark CSV Import/Export - Quick Guide

## Overview

Export your bookmarks to CSV format for backup, sharing, or editing in spreadsheet applications. Import bookmarks from CSV files to restore or transfer data between devices.

## How to Export Bookmarks

1. **Open Bookmarks** → Menu (⋮) → **Export CSV**
2. **Choose location** and filename
3. **Save** to create your CSV file

Or

1. Open Studypad
2. From Window menu, find item "Export as CSV"
3. **Choose location** and filename
4. **Save** to create your CSV file


## How to Import Bookmarks

1. **Open Bookmarks** → Menu (⋮) → **Import CSV**
2. **Select your CSV file**
3. **Review import results**

## CSV Format

AndBible uses **semicolon (`;`) separated** format. Key columns:

- `osisRef` - Scripture reference (e.g., `Gen.1.1-Gen.1.3`)
- `bibleRef` - Human-readable reference (e.g., `Genesis 1:1-3`)
- `ordinalStart/End` - Verse numbers for reliable import
- `labels` - Comma-separated label names
- `notes` - Bookmark text/comments
- `createdAt/lastUpdatedOn` - Timestamps (ISO format)

## Example CSV

```csv
osisRef;bibleRef;labels;notes
Gen.1.1;Genesis 1:1;Creation,Favorite;In the beginning
John.3.16;John 3:16;Salvation;For God so loved the world
```

## Tips

- **Always backup** before importing
- **Use UTF-8 encoding** when creating CSV files
- **Keep semicolon separators** (don't change to commas)
- **Test with small files** first
- **Labels are created automatically** if they don't exist

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Import fails | Check file has proper headers and verse references |
| Characters display wrong | Save CSV file as UTF-8 encoding |
| Can't open file | Verify file location and permissions |

For detailed documentation, see: `/docs/csv-import-export.md`
