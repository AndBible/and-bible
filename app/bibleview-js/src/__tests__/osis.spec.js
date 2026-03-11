/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import crosswireXml from "./testdata/mt.5-crosswire-kjv.xml";
import crosswireResult from "./testdata/mt.5-crosswire-kjv-result.html";


import {useConfig} from "@/composables/config";
import {useStrings} from "@/composables/strings";
import {useAndroid} from "@/composables/android";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {computed, ref} from "vue";
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
    ordinalHighlightKey,
    keyboardKey,
    scrollKey
} from "@/types/constants";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import BookmarkLabelActions from "@/components/modals/BookmarkLabelActions.vue";
import LabelList from "@/components/LabelList.vue";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {useModal} from "@/composables/modal";
import {useKeyboard} from "@/composables/keyboard";
import {useScroll} from "@/composables/scroll";
import { describe, it, expect } from 'vitest'

window.bibleViewDebug = {}
window.bibleView = {
    onKeyDown: () => {},
    setEditing: () => {}
}

function verifyXmlRendering(xmlTemplate, renderedHtml) {
    const {config, appSettings, calculatedConfig} = useConfig(ref("bible"));
    const osisFragment = {
        bookCategory: "BIBLE",
    };

    // Inject missing verseOrdinals into the XML before rendering
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(`<root>${xmlTemplate}</root>`, "text/xml");
    const verses = xmlDoc.getElementsByTagName("verse");
    for (let i = 0; i < verses.length; i++) {
        if (!verses[i].getAttribute("verseOrdinal")) {
            // Use a predictable sequential ordinal for the test
            verses[i].setAttribute("verseOrdinal", (i + 1).toString());
        }
    }
    
    // Serialize the modified XML back to a string
    const serializer = new XMLSerializer();
    const root = xmlDoc.documentElement;
    let processedXml = "";
    for (let i = 0; i < root.childNodes.length; i++) {
        processedXml += serializer.serializeToString(root.childNodes[i]);
    }

    const android = useAndroid({bookmarks: ref([])}, config);
    if (android) {
        android.onKeyDown = android.onKeyDown || (() => {});
        android.setEditing = android.setEditing || (() => {});
    }

    const highlight = useOrdinalHighlight();
    const scroll = useScroll(config, appSettings, calculatedConfig, highlight, ref(null));
    const keyboard = useKeyboard(android, scroll, computed(() => 16));

    const provide = {
        [osisFragmentKey]: osisFragment,
        [configKey]: config,
        [appSettingsKey]: appSettings,
        [calculatedConfigKey]: calculatedConfig,
        [footnoteCountKey]: {getFootNoteCount: () => 0},
        [androidKey]: android,
        [stringsKey]: useStrings(),
        [ordinalHighlightKey]: highlight,
        [globalBookmarksKey]: useGlobalBookmarks(config),
        [modalKey]: useModal(android),
        [keyboardKey]: keyboard,
        [scrollKey]: scroll,
    };
    const components = {AmbiguousSelection, LabelList, BookmarkLabelActions};
    const wrapper = mount(OsisSegment, {props: {osisTemplate: processedXml, convert: true}, global: {provide, components}});
    const vueHtml = wrapper.html();
    //import('fs').then(fs => fs.writeFileSync('./test.html', vueHtml + '\n'));
    expect(vueHtml + "\n").toBe(renderedHtml);
}

describe("OsisSegment.vue", () => {
    it("Test rendering of Eph 2:8 in KJVA, #1985", () => verifyXmlRendering(test1Xml, test1Result));
});

describe("OsisSegment crosswire kjv test", () => {
    it("Test rendering of Crosswire KJV on Mt 5", () => verifyXmlRendering(crosswireXml, crosswireResult));
});
