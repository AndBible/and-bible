/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

// Escapes are for `new RegExp`: \- is a hyphen, \[ and \] are brackets.
// OSIS canonical text writes <q marker> and <milestone marker> into the verse,
// so curly quotes and the paragraph mark have to stay in this one class.
const PUNCT = String.raw`“”¶.,;:!?…"'«»„‚–—\-()\[\]{}`;

const SIMPLE_TOKENIZER = new RegExp(`([${PUNCT}]+)|([^\\s${PUNCT}]+)`, "g");
const PUNCTUATION_ONLY = new RegExp(`^[${PUNCT}]+$`);
// An apostrophe (straight or curly) between two word characters is part of a
// contraction or possessive ("Lord's", "don't") and must stay inside the word.
// A trailing apostrophe stays attached only after "s" ("Jesus'", "years'");
// after any other letter it is a closing quote. Curly apostrophes (’‘) are
// word characters here, so "God’s" stays one token in every mode.
const TYPED_TOKENIZER = new RegExp(
    `([${PUNCT}]+)|([^\\s${PUNCT}]+(?:['’‘][^\\s${PUNCT}]+)*(?:(?<=[sS])['’‘])?)`,
    "g",
);

function tokensOf(text: string, tokenizer: RegExp): string[] {
    const tokens = text.match(tokenizer) || [];
    return tokens.filter(token => token.length > 0);
}

/** Words and punctuation for Order and Scramble. A straight apostrophe is its own token. */
export function tokenizeMemorizeText(text: string): string[] {
    return tokensOf(text, SIMPLE_TOKENIZER);
}

/** Words and punctuation for Type. Apostrophes inside a word stay attached. */
export function tokenizeTypedText(text: string): string[] {
    return tokensOf(text, TYPED_TOKENIZER);
}

/** True when the token is only punctuation or an OSIS marker, so it is not quizzed. */
export function isPunctuation(word: string): boolean {
    return PUNCTUATION_ONLY.test(word);
}
