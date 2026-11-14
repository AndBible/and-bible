# Search

The search form allows searching of Bible and commentaries.

## Overview

Find allows you to search for word(s), phrases, or any combination of words.
Its availability follows the active window's document. If Find is greyed out for a
non-searchable document, select a Bible window or choose a searchable document;
Find becomes available again.

All searches open their results in the reading view's search sheet, including searches
started from selected text, a Strong's link, or another screen. There is no separate
full-screen results screen. The sheet appears on top of the reading text, like a dialog;
it does not add a bottom margin to the reading windows. Tap a result to hide the sheet
and open the passage. Press Back to return to the results list.

In the results sheet's overflow menu, **Open result in links window** controls where an
individual result opens. When checked, the result opens in the links window; when unchecked,
it opens in the active window. This choice takes precedence over the general Links window
setting. Strong's-link searches default to the links window, while searches you type default
to the active window (including a Strong's number you type yourself). AndBible remembers your
choice separately for these two kinds of search.

The separate **Open results in a window** action still opens the whole Bible result list
in a reading window; it does not change the destination of individual result taps.

Tap the **X in the results
sheet** to hide the results without leaving search mode; the **X in the search
toolbar** leaves search mode. You can also swipe the sheet down to dismiss it.
The results sheet has no drag bar: it fits its content rather than offering
multiple sizes. Search options have their own close button too. While an index is
being created, the progress sheet's **X** hides progress without cancelling indexing.

## Bible searches

To search for a specific word in any Bible module tap on the burger menu located at the top left, then tap on **Find**
(or the Find button in the toolbar; on a computer with a keyboard, press Ctrl+F, or ⌘F on a Mac). A search bar opens
on top of the reading text. Enter the word you are looking to find and press the search key on the keyboard.

Tap the sliders button (**Search settings**) in the search bar to choose:

- **Search Where**: `Whole Bible`, `Old Testament`, `New Testament` or the book you are currently viewing (shown by its name).
- The search type: `All words` (every word must be in the verse), `Any word` (at least one of the words) or `Phrase`
  (the words next to each other in that order). The search type is not used when you search for a Strong's number.
- **Translations**: the Bible translations to search; see [Translation selection](#translation-selection).

Tap **Show search results** (the list icon at the left of the search field) to return to the results of your last search.
Tap **Close search** (the back arrow at the left of the search bar) to leave search mode.

The search bar's ⋮ menu has **Rebuild index**, which creates the search index of the document again, and **Help**.
**Help** explains the search syntax briefly and links to this page's [Advanced Search](#advanced-search) section.

If you enter a Bible reference (for example "John 3:16" or "1 joh 3 16"), Find takes you to that passage instead of searching. Abbreviated book names work when the chapter and verse follow, and a reference works before the Bible has a search index: you can type it while Find asks to create the index. A single word is searched as a word, even when it is also a book name such as "Job" or "Mark": type a chapter (for example "Job 1") to jump to the book.

For modules that include the letters of Clement, the English alternative book names are
`iclement` for 1 Clement and `iiclement` for 2 Clement.

For Bible modules that contain Strong's numbers, you can enter the following to find any verses where that number appears:

Note: the word strong must be in lower case.

strong:h3068

strong:G11

### Recent searches

When the search field is empty and you have searched before, a history icon appears at the left of the search field.
Tap it to see your **Recent searches** and tap one of them to search for it again. Once a search has results,
the icon changes to **Show search results**. Clear the field with the **Clear search text** button (the X in the field).

## Translation selection

When you open a Bible search, the active Bible is always selected, together with any
previously selected translations that have a usable search index. Saved translations without
an index are left out of this search without changing your saved selection. This prevents an
unrelated translation from asking to be indexed just because it was selected in an earlier search.

If the active Bible needs an index, AndBible asks to create it. You can also deliberately add
another unindexed translation in the translation selector; AndBible then asks to index it
before searching it. Confirming a translation choice saves that selection for future searches.

## Commentary searches

Commentaries can also be searched using the same search dialog. To search a commentary,
select the commentary as the active document in the current window, then open the search
dialog from the burger menu. The search will be performed against the text of the
commentary entries, even if you previously selected several Bible translations to search.
Your saved Bible selection is kept for the next Bible search.

## Advanced Search

It is possible to do advanced searches using the special syntax described below. This section describes
searching Bibles and commentaries. Searching an EPUB uses a different search engine; see [Searching EPUB books](#searching-epub-books).

### Wildcards

Use `*` to match any number of characters and `?` to match exactly one character. Both can be
used anywhere in a term, at the start, in the middle or at the end:

- `bless*` finds words such as "blessed" and "blessing"
- `*ness` finds words that end in "ness", such as "goodness"
- `b?ess` finds "bless" and other words with one character in place of the `?`

### Combining words

Use `AND`, `OR` and `NOT` to combine terms, and parentheses to group them. These operators, the
symbols below and the parentheses only work when the search mode is 'Any word'. In the 'All words'
mode every word you type is required, so `grace OR faith` is searched as the three required words
"grace", "OR" and "faith" and finds nothing:

- `grace AND faith` finds verses that contain both words
- `grace OR faith` finds verses that contain either word
- `grace NOT faith` finds verses that contain "grace" but not "faith"
- `(grace OR mercy) AND faith` finds verses that contain "faith" together with "grace" or "mercy"

In 'Any word' mode the symbols `&&` (AND), `||` (OR) and `!` (NOT) work as well. A `+` in front of a word means the
word must be present, and a `-` means it must not be present: `+grace -faith`.

Put words in double quotes to search for them as a phrase, that is, next to each other in that
order: `"hope of glory"`. (The 'Phrase' search mode does the same for everything you type.)

### Which search type each syntax needs

The three search types treat what you type differently:

- `Any word` passes your text on unchanged, so all the syntax on this page works there: operators, parentheses,
  quoted phrases, proximity (`"God light"~10`), fuzzy (`believe~0.6`) and wildcards.
- `All words` makes every word you type required. Wildcards and a single fuzzy word still work, but operators,
  quoted phrases and proximity searches do not behave as written, so use `Any word` for them.
- `Phrase` searches everything you type as one phrase. Use it for plain words only.

### Proximity Search

Find words that are within a specific distance of each other. To do a proximity search, put the
words in double quotes and add the tilde, "~", symbol and a number after the closing quote.
For example to search for "God" and "light" within 10 words of each other use the search:

!!! example

    "God light"~10

The words may appear in either order. The number is the maximum distance between the words,
counted in word positions:

- When the words are in the order you typed them, the number must be at least the number of
  words between the two terms.
- When the words are in reverse order, the number must be at least that number plus 2.

### Fuzzy Search

Fuzzy searches find words that are similar in spelling to the search words.
To do a fuzzy search use the tilde, "~", symbol at the end of a single word term followed by a number between 0 and 1.
For example to search for a term similar in spelling to "believe" use the fuzzy search:

!!! example

    believe~0.6

This search will find terms like 'believes', 'believed' etc.

To narrow the search use a larger number. A value of 0.9 is a very near match. A value of 0.3 is a very, very broad match.
The default that is used if the parameter is not given is 0.5, so `believe~` is the same as `believe~0.5`.

### Strong's numbers

Use `strong:` followed by a Strong's number to find verses where that number appears. For example
`strong:h7225` finds Hebrew word H7225, and `strong:g746` finds Greek word G746.

You can also choose **Find all occurrences** from a Strong's definition or dictionary link.
The results open in the same reading-view search sheet, even if the active window displays a
dictionary, an EPUB, or a different Bible. The search uses the selected Strong's-enabled Bible
translations, not the document you happen to be reading. The sheet opens automatically with the
matching verses; you do not need to submit the query again in the toolbar.

If a selected translation needs a usable Strong's search index, the sheet asks to index that
translation by name and shows its progress. With several selected translations, it asks for
any remaining indexes before running the waiting search automatically. Hiding progress does
not cancel indexing. A failed build stays in the search sheet so you can retry; cancelling an
index prompt leaves search without opening a separate results screen.

### Searching EPUB books

When the open document is an EPUB, the search bar's settings have a **Search type** list with four choices:
`All words`, `Any word`, `Phrase` and `Raw query syntax`. EPUB search uses SQLite's FTS5 engine, not the engine
used for Bibles. `Raw query syntax` is **not** the same thing as the [Advanced Search](#advanced-search) section above: it passes
your text to the search engine unchanged, so you can write your own query, for example `grace AND faith`,
`grace OR faith`, `grace NOT faith`, `"hope of glory"`, `bless*`, or `grace NEAR faith` to find words close together.
In the EPUB search bar, **Help** links to the SQLite *FTS5 Full-text Query Syntax* page. Bible searches have no
`Raw query syntax` type.

### Not supported

Some syntax is not supported. If you use it, the search does not run and a message says that the
search uses syntax that is not supported. The following are not supported:

- boosting a term with `^`, for example `grace^2`
- range queries such as `[grace TO hope]`

In the search screen a colon is treated as a space, so `book:john` simply searches for the two words
"book" and "john". Only `strong:` followed by a Strong's number is recognised as a field.

### Search index

After updating the app, searches ask once to create the search index again. Indexing runs in the
background and shows a notification until it has finished. You can leave Search while indexing
continues. Opening Search again for the same document shows the indexing progress rather than
asking you to create the index again. This also applies when the document's index is queued behind
another build, or when you start a search from selected text. If the index has already finished,
Search continues to the search form or runs the query you were waiting for. The progress sheet
shows only running search-index jobs and updates as they progress; unrelated downloads and
finished jobs are not listed.

An empty, damaged or outdated SWORD search index is treated as needing a rebuild, including when
searching from links or AI tools. If a few entries of a document cannot be read during indexing
(up to 5, or 1% of the document when it is large), the index is still created and AndBible tells you
once which entries are not searchable (the first ten, then the number of the rest). If more cannot be read, the
incomplete index is not used, any earlier index stays in place and the affected references are
reported (the first twenty, then the number of the rest). Try reinstalling the document before
building its index again if the read errors persist.

<span id="fallback"></span><span id="search-results"></span>
