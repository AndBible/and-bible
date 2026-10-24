# Search

The search form allows searching of Bible and commentaries.

## Overview

Find allows you to search for word(s), phrases, or any combination of words.

## Bible searches

To search for a specific word in any Bible module tap on the burger menu located at the top left, then tap on "Find" . Then enter the word you are looking to find.
Optionally you can limit your search to only the OT, NT, or the book you are currently viewing.
If you enter multiple words, optionally you can also look for an exact match, as a phrase, or for any of the words appearing any where within a verse.

If you enter a Bible reference (for example "John 3:16" or "1 joh 3 16"), Find takes you to that passage instead of searching. A single word that is also a book name, such as "Job" or "Mark", is treated as a reference too.

For Bible modules that contain Strong's numbers, you can enter the following to find any verses where that number appears:

Note: the word strong must be in lower case.

strong:h3068

strong:G11

## Commentary searches

Commentaries can also be searched using the same search dialog. To search a commentary,
select the commentary as the active document in the current window, then open the search
dialog from the burger menu. The search will be performed against the text of the
commentary entries.

## Advanced Search

It is possible to do advanced searches using the special syntax described below.

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

### Search Option Compatibility

Some searches cannot be performed with other searches.

1. 'All endings' is not compatible with a 'Fuzzy Search'
2. 'All endings' is not compatible with a 'Proximity Search'
3. 'Any word' / 'Phrase' is not compatible with 'Proximity'

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

### Not supported

Some syntax is not supported. If you use it, the search does not run and a message says that the
search uses syntax that is not supported. The following are not supported:

- boosting a term with `^`, for example `grace^2`
- range queries such as `[grace TO hope]`

In the search screen a colon is treated as a space, so `book:john` simply searches for the two words
"book" and "john". Only `strong:` followed by a Strong's number is recognised as a field.

### Search index

After updating the app, searches ask once to create the search index again. Indexing runs in the
background and shows a notification until it has finished.

<span id="fallback"></span><span id="search-results"></span>
