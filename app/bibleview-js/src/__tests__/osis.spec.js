/*
 * Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import {mount} from "@vue/test-utils";
import OsisSegment from "@/components/documents/OsisSegment.vue";

import test1Xml from "./testdata/eph.2-kjva.xml";
import test1Result from "./testdata/eph.2-kjva-result.html";


import {useConfig} from "@/composables/config";
import {useStrings} from "@/composables/strings";
import {useAndroid} from "@/composables/android";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {ref} from "vue";
import {
    androidKey,
    appSettingsKey,
    calculatedConfigKey,
    configKey,
    footnoteCountKey,
    globalBookmarksKey,
    modalKey,
    osisFragmentKey,
    stringsKey,
    ordinalHighlightKey
} from "@/types/constants";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import BookmarkLabelActions from "@/components/modals/BookmarkLabelActions.vue";
import LabelList from "@/components/LabelList.vue";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {useModal} from "@/composables/modal";
import { describe, it, expect } from 'vitest'

window.bibleViewDebug = {}
window.bibleView = {}

function verifyXmlRendering(xmlTemplate, renderedHtml) {
    const {config, appSettings, calculatedConfig} = useConfig(ref("bible"));
    const osisFragment = {
        bookCategory: "BIBLE",
    };

    const android = useAndroid({bookmarks: null}, config);
    const provide = {
        [osisFragmentKey]: osisFragment,
        [configKey]: config,
        [appSettingsKey]: appSettings,
        [calculatedConfigKey]: calculatedConfig,
        [footnoteCountKey]: {getFootNoteCount: () => 0},
        [androidKey]: android,
        [stringsKey]: useStrings(),
        [ordinalHighlightKey]: useOrdinalHighlight(),
        [globalBookmarksKey]: useGlobalBookmarks(config),
        [modalKey]: useModal(android),
    };
    const components = {AmbiguousSelection, LabelList, BookmarkLabelActions};
    const wrapper = mount(OsisSegment, {props: {osisTemplate: xmlTemplate, convert: true}, global: {provide, components}});
    expect(wrapper.html() + "\n").toBe(renderedHtml);
}

function mountOsisSegment(xmlTemplate, {showRedLetters = false, convert = true, isNativeHtml = false} = {}) {
    const {config, appSettings, calculatedConfig} = useConfig(ref("bible"));
    config.showRedLetters = showRedLetters;

    const osisFragment = {
        bookCategory: "BIBLE",
    };

    const android = useAndroid({bookmarks: null}, config);
    const provide = {
        [osisFragmentKey]: osisFragment,
        [configKey]: config,
        [appSettingsKey]: appSettings,
        [calculatedConfigKey]: calculatedConfig,
        [footnoteCountKey]: {getFootNoteCount: () => 0},
        [androidKey]: android,
        [stringsKey]: useStrings(),
        [ordinalHighlightKey]: useOrdinalHighlight(),
        [globalBookmarksKey]: useGlobalBookmarks(config),
        [modalKey]: useModal(android),
    };
    const components = {AmbiguousSelection, LabelList, BookmarkLabelActions};
    return mount(OsisSegment, {props: {osisTemplate: xmlTemplate, convert, isNativeHtml}, global: {provide, components}});
}

describe("OsisSegment.vue", () => {
    // Skipping this now. Need to figure out how to make sure scoped css do not break our test
    // This does not seem to work, for some reason
    // https://runthatline.com/test-css-module-classes-in-vue-with-vitest/
    // https://github.com/AndBible/and-bible/issues/2434
    it.skip("Test rendering of Eph 2:8 in KJVA, #1985", () => verifyXmlRendering(test1Xml, test1Result));

    it("renders MyBible J tags as red letters when enabled", () => {
        const wrapper = mountOsisSegment("<J>Jesus words</J>", {showRedLetters: true});
        expect(wrapper.find(".redLetters").exists()).toBe(true);
        expect(wrapper.find(".redLetters").text()).toBe("Jesus words");
    });

    it("does not add red letter class when disabled", () => {
        const wrapper = mountOsisSegment("<J>Jesus words</J>", {showRedLetters: false});
        expect(wrapper.find(".redLetters").exists()).toBe(false);
    });

    // Document content is data, not a Vue template. Mustache braces occurring in it (markdown
    // imported into MyDocuments, EPUB text, module text) must be rendered literally instead of
    // being compiled as interpolation expressions, which would throw and kill the whole render.
    describe("mustache braces in content", () => {
        it("renders braces literally in OSIS content", () => {
            const wrapper = mountOsisSegment("<p>Deadline {{ date }} passed</p>");
            expect(wrapper.text()).toContain("{{ date }}");
        });

        it("renders braces literally in native HTML content", () => {
            const wrapper = mountOsisSegment("<div>Set {{ date }} here</div>",
                {convert: false, isNativeHtml: true});
            expect(wrapper.text()).toContain("{{ date }}");
        });

        it("renders the rest of the content around braces", () => {
            const wrapper = mountOsisSegment("<p>before {{ unknownVariable }} after</p>");
            expect(wrapper.text()).toContain("before");
            expect(wrapper.text()).toContain("after");
        });

        it("renders single braces literally", () => {
            const wrapper = mountOsisSegment("<p>a { b } c</p>");
            expect(wrapper.text()).toContain("a { b } c");
        });
    });
});
