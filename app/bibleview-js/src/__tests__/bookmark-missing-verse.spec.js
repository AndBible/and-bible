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
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {afterEach, describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import {defineComponent, h, nextTick, provide, ref} from "vue";
import Color from "color";
import Verse from "@/components/OSIS/Verse.vue";
import {useBookmarks, useGlobalBookmarks} from "@/composables/bookmarks";
import {useConfig} from "@/composables/config";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {androidKey, appSettingsKey, bibleDocumentInfoKey, calculatedConfigKey, configKey, ordinalHighlightKey, stringsKey} from "@/types/constants";

window.bibleViewDebug = {};

// Issue #3515: a Bible module that lacks some verses of a chapter (e.g. Jer 17:1-4 in the
// Apostle's Bible) still gets the bookmarks stored on those verses. A note with text on such a
// missing verse must not stop the other bookmarks of the chapter from being highlighted.
describe("bookmarks on verses missing from the module", () => {
    let wrapper, gb;
    const renderedOrdinals = [5, 6, 7, 8, 9, 10];
    const bookmark = (id, ordinalRange, notes = null) => ({
        id, ordinalRange, offsetRange: null, labels: [1], bookInitials: "AB",
        wholeVerse: true, type: "bookmark", notes, editAction: {},
    });

    async function renderChapter() {
        wrapper = mount(defineComponent({setup() {
            const {config, appSettings, calculatedConfig} = useConfig();
            config.showVerseNumbers = false;
            provide(configKey, config);
            provide(appSettingsKey, appSettings);
            provide(calculatedConfigKey, calculatedConfig);
            provide(stringsKey, {});
            provide(androidKey, {querySelection: () => null});
            provide(ordinalHighlightKey, useOrdinalHighlight());
            provide(bibleDocumentInfoKey, {ordinalRange: [1, 10], bookInitials: "AB"});
            gb = useGlobalBookmarks(config, ref("bible"));
            useBookmarks("missing", [1, 10], gb, "AB", null, true, ref(true),
                {adjustedColor: c => Color(c)}, config, appSettings);
            return () => h("div", {id: "doc-missing"}, renderedOrdinals.map(ord => h(Verse,
                {osisID: `Jer.17.${ord}`, verseOrdinal: String(ord)}, () => `Verse ${ord}.`)));
        }}), {attachTo: document.body});
        gb.updateBookmarkLabels([{id: 1, color: 0xFFFF0000,
            displayStyle: "HIGHLIGHT", displayStyleWholeVerse: "HIGHLIGHT"}]);
        await nextTick();
    }

    afterEach(() => {
        window.bibleViewDebug.removeHighLights();
        wrapper.unmount();
    });

    it.each([
        ["with text", "Note on a missing verse"],
        ["without text", null],
    ])("keeps a later bookmark highlighted when a note %s is on a missing verse", async (_case, notes) => {
        await renderChapter();
        gb.updateBookmarks([bookmark(1, [4, 4], notes), bookmark(2, [10, 10], "Note on verse 10")]);
        await nextTick();
        expect(document.querySelector("#doc-missing #o-10").classList).toContain("bookmarked");
        expect(document.querySelector("#doc-missing #v-10 .bookmark-marker")).not.toBeNull();
    });

    it("keeps a later marker when a marker-only bookmark is on a missing verse", async () => {
        await renderChapter();
        // Offset bookmarks made in another translation are drawn as markers only.
        gb.updateBookmarks([{...bookmark(1, [4, 4], "Note made in KJV"), offsetRange: [0, 5], wholeVerse: false, bookInitials: "KJV"},
            {...bookmark(2, [10, 10], "Note made in KJV"), offsetRange: [0, 5], wholeVerse: false, bookInitials: "KJV"}]);
        await nextTick();
        expect(document.querySelector("#doc-missing #v-10 .bookmark-marker")).not.toBeNull();
    });

    it("highlights the present part of a bookmark that starts on a missing verse", async () => {
        await renderChapter();
        gb.updateBookmarks([bookmark(1, [3, 6], "Spans missing and present verses"),
            bookmark(2, [10, 10], "Note on verse 10")]);
        await nextTick();
        const highlighted = [...document.querySelectorAll("#doc-missing .bookmarked")].map(e => e.id);
        expect(highlighted).toEqual(["o-5", "o-6", "o-10"]);
        expect(document.querySelector("#doc-missing #v-6 .bookmark-marker")).not.toBeNull();
    });
});
