# CSV Import/Export for Bible Bookmarks

AndBible now supports importing and exporting Bible bookmarks in CSV (Comma-Separated Values) format. This feature allows you to:

- **Backup your bookmarks** to external storage or cloud services
- **Share bookmarks** with other AndBible users
- **Migrate bookmarks** between devices
- **Bulk edit bookmarks** using spreadsheet applications
- **Import bookmarks** from other Bible study applications

## Quick Start

### Exporting Bookmarks

1. Open the **Bookmarks** activity from the main menu
2. Tap the **menu** button (three dots) in the action bar
3. Select **Export CSV**
4. Choose a location and filename for your export file
5. Tap **Save** to export your bookmarks

Or

1. Open Studypad
2. From Window menu, find item "Export as CSV"
3. Choose a location and filename for your export file
4. Tap **Save** to export your bookmarks


### Importing Bookmarks

1. Open the **Bookmarks** activity from the main menu
2. Tap the **menu** button (three dots) in the action bar
3. Select **Import CSV**
4. Browse and select your CSV file
5. Review the import results

## CSV Format Specification

AndBible uses **semicolon (`;`) separated** CSV format to handle commas that may appear in bookmark notes. The CSV file includes the following columns:

### Required Fields

| Column | Description | Example |
|--------|-------------|---------|
| `osisRef` | OSIS scripture reference (primary method) | `Gen.1.1-Gen.1.3` |
| `bibleRef` | Human-readable Bible reference | `Genesis 1:1-3` |

### Optional Fields

| Column          | Description                                     | Example                        |
|-----------------|-------------------------------------------------|--------------------------------|
| `id`            | Unique bookmark identifier                      | `12345`                        |
| `document`      | Bible translation/version                       | `ESV`                          |
| `book`          | OSIS book abbreviation                          | `Gen`                          |
| `document`      | Bible document initials                         | `KJV`                          |
| `chapterStart`  | Starting chapter number                         | `1`                            |
| `verseStart`    | Starting verse number                           | `1`                            |
| `chapterEnd`    | Ending chapter number                           | `1`                            |
| `verseEnd`      | Ending verse number                             | `3`                            |
| `ordinalStart`  | Starting verse ordinal number                   | `1`                            |
| `ordinalEnd`    | Ending verse ordinal number                     | `3`                            |
| `createdAt`     | Creation timestamp (ISO format)                 | `2025-01-15T10:30:00Z`         |
| `lastUpdatedOn` | Last modification timestamp                     | `2025-01-15T10:30:00Z`         |
| `startOffset`   | Text selection start position                   | `0`                            |
| `endOffset`     | Text selection end position                     | `25`                           |
| `labels`        | Comma-separated label names                     | `Favorite,Study Notes`         |
| `notes`         | Bookmark notes/comments                         | `Important passage for prayer` |
| `customIcon`    | Custom icon identifier                          | `star`                         |

## Verse Reference Methods

AndBible supports multiple ways to specify verse references in your CSV file. The import process tries these methods in order:

### 1. Ordinal Numbers (Recommended)
Use `ordinalStart` and `ordinalEnd` columns for the most reliable import:
```csv
ordinalStart;ordinalEnd
1;3
```

### 2. OSIS References
Use the `osisRef` column for standard OSIS format:
```csv
osisRef
Gen.1.1-Gen.1.3
```

### 3. Discrete Book/Chapter/Verse
Use separate columns for each component:
```csv
book;chapterStart;verseStart;chapterEnd;verseEnd
Gen;1;1;1;3
```

### 4. Bible References
Use the `bibleRef` column for human-readable references:
```csv
bibleRef
Genesis 1:1-3
```

## Labels and Categories

### Exporting Labels
- Labels are exported as comma-separated values in the `labels` column
- Example: `Favorite,Prayer,Study Notes`

### Importing Labels
- Labels that don't exist will be created automatically
- Label names are case-sensitive
- Empty or whitespace-only labels are ignored
- New labels are assigned the default system color

## Sample CSV File

Here's an example of a complete CSV file:

```csv
osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV;Gen;1;1;1;1;1;1;1;2025-01-15T10:30:00Z;2025-01-15T10:30:00Z;;;Creation,Favorite;In the beginning God created;
John.3.16;John 3:16;ESV;John;3;16;3;16;2;26137;26137;2025-01-15T11:00:00Z;2025-01-15T11:00:00Z;;;Salvation,Love;For God so loved the world;
```

## Tips and Best Practices

### For Exporting
- **Regular backups**: Export your bookmarks regularly to prevent data loss
- **Cloud storage**: Save exports to Google Drive, Dropbox, or other cloud services
- **Naming convention**: Use descriptive filenames like `andbible-bookmarks-2025-01-15.csv`

### For Importing
- **Backup first**: Always export existing bookmarks before importing
- **Test with small files**: Try importing a few bookmarks first to verify format
- **Check encoding**: Ensure your CSV file is saved in UTF-8 encoding
- **Review results**: Check the import summary for any errors or warnings

### For Editing
- **Use proper tools**: Excel, LibreOffice Calc, or Google Sheets work well
- **Preserve semicolons**: Don't change the semicolon separators to commas
- **Quote fields**: Enclose fields containing semicolons or quotes in double quotes
- **Date format**: Use ISO format (YYYY-MM-DDTHH:MM:SSZ) for timestamps

## Troubleshooting

### Common Import Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Could not determine verse range" | Missing or invalid verse reference | Ensure at least one verse reference method is provided |
| "Invalid OSIS reference" | Malformed OSIS format | Check OSIS reference syntax (e.g., `Gen.1.1`) |
| "Empty CSV file" | File has no content | Verify file contains headers and data |
| "Invalid data on line X" | Malformed CSV row | Check row formatting and field count |

### File Format Issues

- **Encoding problems**: Save CSV files in UTF-8 encoding
- **Separator issues**: Use semicolons (`;`) not commas (`,`) as separators
- **Quote handling**: Escape quotes in text fields by doubling them (`""`)
- **Line endings**: Use standard line endings (CRLF or LF)

### Performance Considerations

- **Large files**: Import may take longer for files with thousands of bookmarks
- **Memory usage**: Very large imports may require closing other apps
- **Storage space**: Ensure sufficient storage for both CSV file and bookmark data

## Technical Details

### Character Encoding
- CSV files must be UTF-8 encoded
- Supports international characters and symbols
- Handles special characters in notes and labels

### Date Format
- Timestamps use ISO 8601 format: `YYYY-MM-DDTHH:MM:SSZ`
- All times are stored in UTC timezone
- Missing timestamps default to current date/time

### Field Escaping
- Fields containing semicolons, quotes, or newlines are automatically quoted
- Quotes within fields are escaped by doubling: `"He said ""Hello"""`
- Newlines in notes are preserved within quoted fields

## Integration with Other Applications

### Spreadsheet Applications
- **Microsoft Excel**: Open CSV files directly, mind the semicolon separator
- **Google Sheets**: Import CSV and specify semicolon as delimiter
- **LibreOffice Calc**: Choose semicolon separator during import

### Bible Study Software
- Format may be compatible with other applications using OSIS references
- May require column mapping or format conversion
- Check target application's CSV import requirements

## Privacy and Security

- **Local processing**: All import/export operations happen locally on your device
- **No cloud transmission**: AndBible doesn't send your data to external servers
- **File permissions**: Choose secure storage locations for sensitive bookmarks
- **Backup encryption**: Consider encrypting exported files if they contain sensitive notes

## Support and Feedback

If you encounter issues with CSV import/export:

1. Check this documentation for troubleshooting tips
2. Visit the [AndBible Wiki](https://github.com/andbible/and-bible/wiki)
3. Report bugs on [GitHub Issues](https://github.com/AndBible/and-bible/issues)
4. Join discussions on the [AndBible Community](https://github.com/AndBible/and-bible/discussions)

---

*This feature was introduced in AndBible version 5.0.882. For older versions, consider using the backup/restore functionality in Settings.*
