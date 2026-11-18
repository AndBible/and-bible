# My Documents and EPUB books {#my-documents}

*AndBible* allows you to create your own custom documents, such as personal study
guides, sermon outlines, or reference notes. These documents appear alongside
Bibles and commentaries in the document library and can be opened in any window.

## Creating a Document

1. From the top left main menu (☰), click `My Documents`.
2. Click the `+` button (*New*) to create a new document.
3. In the *Create new document* sheet, edit the suggested name (*Document 1*, *Document 2*,
    …) and click `OK`.

The new document is created at once and needs no *Save*. The same sheet also has
*Import files as document*, which creates a document from Markdown or HTML files on your
device.

## Managing Documents

From the My Documents screen you can:

- **Reorder** documents by dragging the handle at the start of a row.
- **Rename** a document via the three-dot menu at the end of its row.
- **Edit description** (same menu) to add a short summary of the document’s contents.
- **Export all pages** (same menu) to files.
- **Delete** a document (same menu; requires confirmation).
- **Search** the list with the `Find` icon in the top bar.

A long press on a row does not open the menu; it starts multi-selection, where the top
bar offers export and delete for all the selected documents.

Renaming, editing descriptions, reordering and deleting take effect only when you tap
`Save` at the bottom; `Dismiss` throws these changes away.

## Managing Pages

Each document contains one or more pages. Click a document to open its page
list, titled *<document> - Pages*.

- **Add a page** by clicking the `+` button (*New page*). The *New page* sheet has a
    suggested name, the *Content type*, and *Import Markdown/HTML* to create a page
    from a file.
- **Reorder** pages by dragging.
- **Rename**, **export** or **delete** a page via the three-dot menu on its row.
- **Switch document** in the top bar jumps to the pages of another document, and `Find`
    searches the list.
- **Click a page** to open it in a document window for viewing.

As on the document list, changes to the page list (rename, reorder, delete) are applied
with `Save`, and `Dismiss` discards them. A new page is created at once.

## Writing a Page

Click a page in the list to open it in a window. An empty page reads *Tap to start
editing this page*; tap it to start editing. On a page that already has content, tap the
three-dot icon at the start of the page and choose to edit it, share it or copy it.

## Content Formats

A new My Document page can be created in two content formats, chosen with the *Content
type* drop-down in the *New page* sheet:

- **Markdown** (default) – Write using Markdown syntax. See
    [Notes](notes.md) for more about the Markdown editor.
- **HTML** – Raw HTML content.

The content format for each page is shown in the page list. (OSIS, a Bible markup format,
also exists internally, but it cannot be chosen when creating a page.)

## Opening Documents

My Documents are available in the document library alongside other book types.
To open a document in a window:

1. Open the document library (click and hold the document name in the top menu
    bar, or use the main menu).
2. Find your document under the `Book` section.
3. Click the document to open it.

## AI documents are My Documents {#ai-documents-are-my-documents}

When an [AI prompt](ai.md#running-a-prompt) produces a document, the result is saved as a page of
the *AI Documents* document in My Documents. Its pages are Markdown, like any other My Document
page, so everything on this page applies to them too:

- **Edit** a result as you would any page (see [Writing a Page](#writing-a-page)).
- **Share or copy** it from the three-dot icon at the start of the page.
- **Export** one page with *Export* in its three-dot menu on the page list, or all pages of
    the document with *Export all pages* in the document's menu. A page is written as a `.md`
    file; exporting all pages writes one file per page into a folder you choose, numbered in
    page order (`01-…`, `02-…`).

## Exporting and Importing Markdown

Markdown export and import make a round trip for the content (page names may lose punctuation on the way). Export pages to files, edit them in any text
editor or keep them as a backup, and bring them back with *Import files as document* (a new
document, one page per file, in file-name order) or *Import Markdown/HTML* in the *New page*
sheet (one page per file). A file ending in `.html` or `.htm` becomes an HTML page; any other file
becomes a Markdown page. A page takes its name from the file name. The same route works for
files that did not come from *AndBible*, such as notes written elsewhere.

## Cloud Sync

My Documents can be synchronized across devices using [cloud sync](cloud_sync.md). Enable the **My Documents** toggle in the cloud sync settings;
your documents and their contents are then kept in sync between devices. The available sync backends differ by platform; see [Platform differences](platform_differences.md).
