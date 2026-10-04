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

### Search Option Compatibility

Some searches cannot be performed with other searches.

1. 'All endings' is not compatible with a 'Fuzzy Search'
2. 'All endings' is not compatible with a 'Proximity Search'
3. 'Any word' / 'Phrase' is not compatible with 'Proximity'

### Proximity Search

Find words that are a within a specific distance. To do a proximity search use the tilde, "~", symbol at the end of a phrase.
For example to search for a "God" and "light" within 10 words of each other in a document use the search:

!!! example

    "God light"~10

!!! note

    The order of the words are important. The above example will find verses where the word 'God' comes before the word 'light'

### Fuzzy Search

Fuzzy searches find words that are similar in spelling to the search words.
To do a fuzzy search use the tilde, "~", symbol at the end of a single word term followed by a number between 0 and 1.
For example to search for a term similar in spelling to "believe" use the fuzzy search:

!!! example

    believe~0.6

This search will find terms like 'believes', 'believed' etc.

To narrow the search use a larger number. A value of 0.9 is a very near match. A value of 0.3 is a very, very broad match.
The default that is used if the parameter is not given is 0.5.

<span id="fallback"></span><span id="search-results"></span>
