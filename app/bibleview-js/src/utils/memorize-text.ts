/*
 * Copyright (c) 2021-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

// Shared word/punctuation tokenizer for the Memorize views (Type, Scramble, Order).
//
// Punctuation is any Unicode punctuation (\p{P}) plus markers that module text uses as
// editorial signs and that must never be typed or picked as a word: the pilcrow, asterisk
// and slash (all of which are also \p{P}; they are listed explicitly so the intent survives).
//
// An apostrophe (straight or curly) sitting between two word characters is part of a
// contraction/possessive (e.g. "Lord's", "don't") and must not split the word. A bare
// trailing apostrophe is also kept attached when the word ends in "s" (e.g. "Jesus'",
// "years'") since that is the standard classical/plural possessive form; a trailing
// apostrophe after any other letter is treated as a closing quote instead.

const PUNCT = String.raw`\p{P}¶*/`;
const WORD_CHAR = `[^\\s${PUNCT}]`;
const APOSTROPHE = `['’‘]`;

const TOKENIZER = new RegExp(
    `[${PUNCT}]+|${WORD_CHAR}+(?:${APOSTROPHE}${WORD_CHAR}+)*(?:(?<=[sS])${APOSTROPHE})?`,
    'gu',
);
const PUNCTUATION_ONLY = new RegExp(`^[${PUNCT}]+$`, 'u');

/** Splits memorize text into word tokens and punctuation-run tokens; whitespace is dropped. */
export function tokenizeMemorizeText(text: string): string[] {
    return text.match(TOKENIZER) ?? [];
}

/** True when the token consists solely of punctuation/markers that the user never types or picks. */
export function isMemorizePunctuation(token: string): boolean {
    return PUNCTUATION_ONLY.test(token);
}
