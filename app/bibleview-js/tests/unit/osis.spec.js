/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {mount} from "@vue/test-utils";
import OsisSegment from "@/components/documents/OsisSegment";

import test1Xml from "./testdata/eph.2-kjva.xml";
import test1Result from "./testdata/eph.2-kjva-result.html";


import {useConfig} from "@/composables/config";
import {useStrings} from "@/composables/strings";
import {useAndroid} from "@/composables/android";
import {useVerseHighlight} from "@/composables/verse-highlight";
import {ref} from "@vue/reactivity";
import {BookCategories, DocumentTypes} from "@/constants";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection";
import BookmarkLabelActions from "@/components/modals/BookmarkLabelActions";
import LabelList from "@/components/LabelList";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {useModal} from "@/composables/modal";
window.bibleViewDebug = {}
window.bibleView = {}

function verifyXmlRendering(xmlTemplate, renderedHtml) {
    const {config, appSettings, calculatedConfig} = useConfig(ref(DocumentTypes.BIBLE_DOCUMENT));
    const osisFragment = {
        bookCategory: BookCategories.BIBLE,
    };

    const android = useAndroid({bookmarks: null}, config);
    const provide = {
        osisFragment,
        config,
        appSettings,
        calculatedConfig,
        footNoteCount: {getFootNoteCount: () => 0},
        android,
        strings: useStrings(),
        verseHighlight: useVerseHighlight(),
        globalBookmarks: useGlobalBookmarks(config),
        modal: useModal(android),
    };
    const components = {AmbiguousSelection, LabelList, BookmarkLabelActions};
    const wrapper = mount(OsisSegment, {props: {osisTemplate: xmlTemplate, convert: true}, global: {provide, components}});
    expect(wrapper.html() + "\n").toBe(renderedHtml);
}

describe("OsisSegment.vue", () => {
    it("Test rendering of Eph 2:8 in KJVA, #1985", () => verifyXmlRendering(test1Xml, test1Result));
});
