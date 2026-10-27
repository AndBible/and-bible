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

describe("bookmark verse spacing", () => {
    let wrapper, gb;
    const bookmark = (offsetRange = null, bookInitials = "KJV") => ({
        id: 1, ordinalRange: [1, 1], offsetRange, labels: [1], bookInitials,
        wholeVerse: offsetRange === null, type: "bookmark", customIcon: "star", editAction: {},
    });

    async function renderVerses(settings = {}) {
        wrapper = mount(defineComponent({setup() {
            const {config, appSettings, calculatedConfig} = useConfig();
            config.showVerseNumbers = false;
            Object.assign(appSettings, settings);
            provide(configKey, config);
            provide(appSettingsKey, appSettings);
            provide(calculatedConfigKey, calculatedConfig);
            provide(stringsKey, {});
            provide(androidKey, {querySelection: () => null});
            provide(ordinalHighlightKey, useOrdinalHighlight());
            provide(bibleDocumentInfoKey, {ordinalRange: [1, 2], bookInitials: "KJV"});
            gb = useGlobalBookmarks(config, ref("bible"));
            useBookmarks("spacing", [1, 2], gb, "KJV", null, true, ref(true),
                {adjustedColor: c => Color(c)}, config, appSettings);
            return () => h("div", {id: "doc-spacing"}, [1, 2].map(ord => h(Verse,
                {osisID: `Gen.1.${ord}`, verseOrdinal: String(ord)},
                () => ord === 1 ? ["First ", h("em", "verse.")] : "Second verse.")));
        }}), {attachTo: document.body});
        gb.updateBookmarkLabels([{id: 1, color: 0xFFFF0000,
            displayStyle: "HIGHLIGHT", displayStyleWholeVerse: "HIGHLIGHT"}]);
        await nextTick();
    }

    afterEach(() => {
        window.bibleViewDebug.removeHighLights();
        wrapper.unmount();
    });

    function expectMarkerBeforeSeparator() {
        const verse = document.querySelector("#doc-spacing #v-1");
        const marker = verse.querySelector(".bookmark-marker");
        expect(marker).not.toBeNull();
        const beforeMarker = document.createRange();
        beforeMarker.setStart(verse, 0);
        beforeMarker.setEndBefore(marker);
        expect(beforeMarker.toString()).toBe("First verse.");
        const afterMarker = document.createRange();
        afterMarker.setStartAfter(marker);
        afterMarker.setEndAfter(document.querySelector("#doc-spacing #v-2"));
        expect(afterMarker.toString()).toBe(" Second verse. ");
    }

    it.each([
        ["light", {}],
        ["dark", {nightMode: true}],
        ["monochrome", {monochromeMode: true, pureMonochromeMode: true}],
        ["no animations", {disableAnimations: true}],
    ])("keeps whole-verse highlighting and its icon before the separator in %s mode", async (_mode, settings) => {
        await renderVerses(settings);
        gb.updateBookmarks([bookmark()]);
        await nextTick();
        expect(document.querySelector("#doc-spacing .bookmarked").textContent).toBe("First verse.");
        expect(document.querySelector("#doc-spacing #o-2.bookmarked")).toBeNull();
        expectMarkerBeforeSeparator();

        gb.clearBookmarks();
        await nextTick();
        expect(document.querySelectorAll("#doc-spacing .bookmarked, #doc-spacing .bookmark-marker")).toHaveLength(0);
        expect(wrapper.element.textContent).toBe("First verse. Second verse. ");
    });

    it("keeps marker-only bookmarks from another translation before the separator", async () => {
        await renderVerses();
        gb.updateBookmarks([bookmark([1, 4], "RVR")]);
        await nextTick();
        expectMarkerBeforeSeparator();
    });

    it("preserves partial bookmark offsets across nested text", async () => {
        await renderVerses();
        gb.updateBookmarks([bookmark([3, 8])]);
        await nextTick();
        expect([...document.querySelectorAll("#doc-spacing .bookmarked")].map(e => e.textContent).join("")).toBe("st ve");
        const beforeMarker = document.createRange();
        beforeMarker.setStart(document.querySelector("#doc-spacing #v-1"), 0);
        beforeMarker.setEndBefore(document.querySelector("#doc-spacing .bookmark-marker"));
        expect(beforeMarker.toString()).toBe("First ve");
        gb.clearBookmarks();
        await nextTick();
        expect(wrapper.element.textContent).toBe("First verse. Second verse. ");
        expect(wrapper.find("em").text()).toBe("verse.");
    });
});
