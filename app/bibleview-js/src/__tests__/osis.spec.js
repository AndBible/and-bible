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


import {strongsModes, useConfig} from "@/composables/config";
import {useStrings} from "@/composables/strings";
import {useAndroid} from "@/composables/android";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {nextTick, ref} from "vue";
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

function mountOsisSegment(xmlTemplate, {showRedLetters = false, convert = true, isNativeHtml = false,
    strongsMode = strongsModes.hidden, ordinalHighlight = useOrdinalHighlight()} = {}) {
    const {config, appSettings, calculatedConfig} = useConfig(ref("bible"));
    config.showRedLetters = showRedLetters;
    config.strongsMode = strongsMode;

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
        [ordinalHighlightKey]: ordinalHighlight,
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

    it("highlights list item text within the selected verse and clears it on deselection (#3785)", async () => {
        const ordinalHighlight = useOrdinalHighlight();
        const wrapper = mountOsisSegment(
            '<verse osisID="Deut.14.12" verseOrdinal="100">Do not eat these birds:' +
            '<list><item>the eagle</item><item>the vulture</item></list></verse>' +
            '<verse osisID="Deut.14.13" verseOrdinal="101"><list><item>the kite</item></list></verse>',
            {ordinalHighlight});
        try {
            const items = wrapper.findAll("li");
            expect(items.map(item => item.text())).toEqual(["the eagle", "the vulture", "the kite"]);
            expect(wrapper.findAll(".isHighlighted")).toHaveLength(0);

            ordinalHighlight.highlightOrdinal(100);
            await nextTick();
            expect(wrapper.get("#v-100 .highlight-transition").classes()).toContain("isHighlighted");
            // An inline ancestor's background does not paint text in a block-level list item.
            for (const item of items.slice(0, 2)) {
                expect(item.get(".isHighlighted").text()).toBe(item.text());
                expect(item.get(".isHighlighted").classes()).toContain("highlight-transition");
            }
            expect(items[2].find(".isHighlighted").exists()).toBe(false);

            ordinalHighlight.resetHighlights();
            await nextTick();
            expect(wrapper.findAll(".isHighlighted")).toHaveLength(0);
        } finally {
            wrapper.unmount();
        }
    });

    it("leaves standalone lists unhighlighted when a different verse is selected", async () => {
        const ordinalHighlight = useOrdinalHighlight();
        const wrapper = mountOsisSegment(
            '<list><item>Document introduction</item></list>' +
            '<verse osisID="Deut.14.12" verseOrdinal="100">Verse text</verse>',
            {ordinalHighlight});
        try {
            ordinalHighlight.highlightOrdinal(100);
            await nextTick();
            expect(wrapper.get("#v-100 .isHighlighted").text()).toContain("Verse text");
            expect(wrapper.get("li").find(".isHighlighted").exists()).toBe(false);
        } finally {
            wrapper.unmount();
        }
    });

    // Original OSIS from SpaRV1909 1.0.1 (Gen 1:1 and Matt 1:1), issue #3229.
    it.each([
        ['<w lemma="Strong:H7225">EN el principio</w>', 'ab-w://?strong=H7225'],
        ['<w lemma="Strong:G0976">LIBRO</w>', 'ab-w://?strong=G0976'],
        ['<w lemma="strong:G2424 strong:G5547">de Jesucristo</w>', 'ab-w://?strong=G5547&strong=G2424'],
    ])("creates a recognised Strong's link from SpaRV1909 markup %s", (markup, href) => {
        const wrapper = mountOsisSegment(markup, {strongsMode: strongsModes.links});
        expect(wrapper.get("a.strongs").attributes("href")).toBe(href);
        wrapper.unmount();
    });

    it("normalises only the Strong's namespace, preserving targets and other namespaces", () => {
        const wrapper = mountOsisSegment(
            '<w lemma="STRONG:G0976 strong:H0430 lemma.TR:AbC" morph="robinson:N-NSM">word</w>',
            {strongsMode: strongsModes.links});
        expect(wrapper.get("a.strongs").attributes("href")).toBe(
            "ab-w://?lemma.TR=AbC&strong=H0430&strong=G0976&robinson=N-NSM");
        wrapper.unmount();
    });

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
