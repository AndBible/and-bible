/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
 * If not, see http://www.gnu.org/licenses/.
 */

import {annotateTitles} from "@/utils";
import { describe, it, expect } from 'vitest'

describe("annotateTitles", () => {
    it("annotates a single title before a verse", () => {
        const input = `<title type="section" canonical="true">Introduction</title><verse sID="a" osisID="Gen.1.1" verseOrdinal="100"/>`;
        const result = annotateTitles(input);
        expect(result).toContain(`<title ordinal="100" titleIndex="1" type="section" canonical="true">`);
    });

    it("annotates multiple titles before the same verse with increasing titleIndex", () => {
        const input = `<title type="section" canonical="true">Section 1</title><title type="sub">Subsection A</title><verse sID="a" osisID="Gen.1.1" verseOrdinal="50"/>`;
        const result = annotateTitles(input);
        expect(result).toContain(`<title ordinal="50" titleIndex="1" type="section" canonical="true">`);
        expect(result).toContain(`<title ordinal="50" titleIndex="2" type="sub">`);
    });

    it("preserves existing title attributes", () => {
        const input = `<title type="main" canonical="true" short="The Book">The Book of Genesis</title><verse sID="a" osisID="Gen.1.1" verseOrdinal="1"/>`;
        const result = annotateTitles(input);
        expect(result).toContain(`<title ordinal="1" titleIndex="1" type="main" canonical="true" short="The Book">`);
    });

    it("skips titles that appear after the last verse", () => {
        const input = `<verse sID="a" osisID="Gen.1.1" verseOrdinal="10"/><title type="section">Colophon</title>`;
        const result = annotateTitles(input);
        expect(result).toBe(input);
    });

    it("returns the input unchanged when there are no verses", () => {
        const input = `<title type="section">Orphan title</title>`;
        const result = annotateTitles(input);
        expect(result).toBe(input);
    });

    it("handles multiple verses with titles between them", () => {
        const input = `<title canonical="true">Chapter 1</title><verse sID="a" osisID="Gen.1.1" verseOrdinal="10"/><title canonical="true">Subsection</title><verse sID="b" osisID="Gen.1.2" verseOrdinal="20"/>`;
        const result = annotateTitles(input);
        expect(result).toContain(`<title ordinal="10" titleIndex="1" canonical="true">`);
        expect(result).toContain(`<title ordinal="20" titleIndex="1" canonical="true">`);
    });

    it("handles titles with no attributes (bare <title>)", () => {
        const input = `<title>Bare title</title><verse sID="a" osisID="Gen.1.1" verseOrdinal="5"/>`;
        const result = annotateTitles(input);
        expect(result).toContain(`<title ordinal="5" titleIndex="1">`);
    });
});
