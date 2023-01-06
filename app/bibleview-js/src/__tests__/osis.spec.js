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


import {useConfig} from "@/composables/config";
import {useStrings} from "@/composables/strings";
import {useAndroid} from "@/composables/android";
import {useVerseHighlight} from "@/composables/verse-highlight";
import {ref} from "vue";
import {
    androidKey,
    appSettingsKey,
    BookCategories,
    calculatedConfigKey,
    configKey,
    DocumentTypes, footnoteCountKey, globalBookmarksKey, modalKey,
    osisFragmentKey, stringsKey, verseHighlightKey
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
    const {config, appSettings, calculatedConfig} = useConfig(ref(DocumentTypes.BIBLE_DOCUMENT));
    const osisFragment = {
        bookCategory: BookCategories.BIBLE,
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
        [verseHighlightKey]: useVerseHighlight(),
        [globalBookmarksKey]: useGlobalBookmarks(config),
        [modalKey]: useModal(android),
    };
    const components = {AmbiguousSelection, LabelList, BookmarkLabelActions};
    const wrapper = mount(OsisSegment, {props: {osisTemplate: xmlTemplate, convert: true}, global: {provide, components}});
    expect(wrapper.html() + "\n").toBe(renderedHtml);
}

describe("OsisSegment.vue", () => {
    // Skipping this now. Need to figure out how to make sure scoped css do not break our test
    // This does not seem to work, for some reason
    // https://runthatline.com/test-css-module-classes-in-vue-with-vitest/
    it.skip("Test rendering of Eph 2:8 in KJVA, #1985", () => verifyXmlRendering(test1Xml, test1Result));
});
