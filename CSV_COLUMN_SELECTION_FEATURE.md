# CSV Column Selection Feature

## Overview
Added a column selection dialog to the CSV export functionality in BookmarkControl. Users can now choose which columns to include in the CSV export.

## Changes Made

### 1. BookmarkCsvUtils.kt
- Added `CsvColumn` data class to represent CSV columns
- Added `availableColumns` list with all available CSV columns and their display names
- Modified `exportBookmarksToCsv` to accept a `selectedColumns` parameter
- Updated export logic to only include selected columns

### 2. BookmarkControl.kt
- Added `showColumnSelectionDialog()` method to display a multi-select dialog
- Modified `exportBookmarksToCSV()` to show column selection dialog before export
- Added `importBookmarksFromCSV()` method that was missing
- Updated `exportToUri()` to pass selected columns to the CSV export

### 3. String Resources
- Added new strings for column selection dialog:
  - `csv_column_selection_title`
  - `csv_column_selection_message`
  - `csv_select_all`
  - `csv_select_none`
- Added Finnish translations for the new strings

### 4. Features
- **Multi-select dialog**: Users can select/deselect individual columns
- **Select All/None toggle**: Button to quickly select or deselect all columns
- **Default selection**: All columns are selected by default
- **User-friendly names**: Column names are displayed in human-readable format
- **Backward compatibility**: Existing CSV import functionality remains unchanged

## Usage
1. User selects "Export to CSV" from the bookmark menu
2. A dialog appears showing all available columns with checkboxes
3. User can select which columns to include
4. "Select All" button toggles between selecting all and selecting none
5. User clicks "OK" to proceed with export or "Cancel" to abort
6. If columns are selected, the file picker opens for saving the CSV

## Available Columns
- OSIS Reference
- Bible Reference  
- Document
- Book
- Chapter Start/End
- Verse Start/End
- ID
- Ordinal Start/End
- Created At
- Last Updated
- Start/End Offset
- Labels
- Notes
- Custom Icon

## Testing
- Added `BookmarkCsvColumnSelectionTest.kt` to verify column selection functionality
- All existing CSV tests continue to pass
- Compilation successful with no errors
