/*
 * Copyright (c) 2020-2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {shallowMount} from "@vue/test-utils";
import Note from "@/components/OSIS/Note.vue";
import {useStrings} from "@/composables/strings";
import {useConfig} from "@/composables/config";
import {ref} from "vue";
import {configKey, stringsKey, footnoteCountKey, osisFragmentKey, exportModeKey} from "@/types/constants";
import {describe, it, expect, beforeEach} from 'vitest'

window.bibleViewDebug = {}

describe("Note.vue", () => {
    let config;
    let provide;
    let footNoteCounter;

    beforeEach(() => {
        const configResult = useConfig(ref("bible"));
        config = configResult.config;
        footNoteCounter = 0;

        provide = {
            [configKey]: config,
            [stringsKey]: useStrings(),
            [footnoteCountKey]: {getFootNoteCount: () => footNoteCounter++},
            [osisFragmentKey]: {v11n: "KJV"},
            [exportModeKey]: ref(false),
        }
    });

    describe("Inline footnotes", () => {
        it("renders footnote as handle when showFootNotesInline is false", () => {
            config.showFootNotes = true;
            config.showFootNotesInline = false;

            const wrapper = shallowMount(Note, {
                props: {type: "explanation"},
                slots: {default: "This is a footnote"},
                global: {provide}
            });

            // Should show handle (letter), not inline content
            expect(wrapper.find('.noteHandle').exists()).toBe(true);
            expect(wrapper.find('.footnote-inline').exists()).toBe(false);
        });

        it("renders footnote inline when showFootNotesInline is true", () => {
            config.showFootNotes = true;
            config.showFootNotesInline = true;

            const wrapper = shallowMount(Note, {
                props: {type: "explanation"},
                slots: {default: "This is a footnote"},
                global: {provide}
            });

            // Should show inline content, not handle
            expect(wrapper.find('.footnote-inline').exists()).toBe(true);
            expect(wrapper.find('.noteHandle').exists()).toBe(false);
        });

        it("does not render inline footnote when showFootNotes is false", () => {
            config.showFootNotes = false;
            config.showFootNotesInline = true;

            const wrapper = shallowMount(Note, {
                props: {type: "explanation"},
                slots: {default: "This is a footnote"},
                global: {provide}
            });

            // Should not show inline content when footnotes are disabled
            expect(wrapper.find('.footnote-inline').exists()).toBe(false);
            expect(wrapper.find('.noteHandle').exists()).toBe(false);
        });

        it("cross-references are not affected by showFootNotesInline", () => {
            config.showFootNotes = true;
            config.showFootNotesInline = true;
            config.showXrefs = true;
            config.expandXrefs = false;

            const wrapper = shallowMount(Note, {
                props: {type: "crossReference"},
                slots: {default: "Matt 1:1"},
                global: {provide}
            });

            // Cross-references should use their own expandXrefs setting, not showFootNotesInline
            expect(wrapper.find('.footnote-inline').exists()).toBe(false);
            expect(wrapper.find('.noteHandle').exists()).toBe(true);
        });

        it("renders different footnote types inline", () => {
            config.showFootNotes = true;
            config.showFootNotesInline = true;

            const footnoteTypes = ["explanation", "translation", "study", "variant", "alternative"];

            for (const type of footnoteTypes) {
                const wrapper = shallowMount(Note, {
                    props: {type},
                    slots: {default: `Footnote of type ${type}`},
                    global: {provide}
                });

                expect(wrapper.find('.footnote-inline').exists()).toBe(true);
            }
        });
    });
});
