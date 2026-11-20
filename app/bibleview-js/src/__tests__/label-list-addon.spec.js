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

import {mount} from "@vue/test-utils";
import {reactive} from "vue";
import {describe, it, expect, vi} from "vitest";
import LabelList from "@/components/LabelList.vue";
import {androidKey, globalBookmarksKey} from "@/types/constants";

const appSettings = reactive({
    monochromeMode: false,
    favouriteLabels: ["own", "addon", "assigned-addon"],
    recentLabels: ["addon-recent"],
    frequentLabels: [],
});

vi.mock("@/composables", () => ({
    useCommon: () => ({appSettings, adjustedColor: () => ({isLight: () => true, string: () => "#fff"})}),
}));

function label(id, addonModule = null) {
    return {id, name: id, color: 0xFF0000FF, addonModule};
}

function mountList(props) {
    const labels = reactive(new Map([
        ["own", label("own")],
        ["addon", label("addon", "MyAddon")],
        ["assigned-addon", label("assigned-addon", "MyAddon")],
        ["addon-recent", label("addon-recent", "")],
    ]));
    const bookmark = {id: "b1", labels: ["assigned-addon"], primaryLabelId: "assigned-addon"};
    return mount(LabelList, {
        props: {bookmarkId: "b1", ...props},
        global: {
            provide: {
                [androidKey]: {},
                [globalBookmarksKey]: {bookmarkMap: reactive(new Map([["b1", bookmark]])), bookmarkLabels: labels},
            },
            stubs: {FontAwesomeIcon: true, BookmarkLabelActions: true},
        },
    });
}

describe("LabelList quick-assign chips", () => {
    it("does not offer unassigned add-on labels from favourites", () => {
        const names = mountList({favourites: true}).findAll(".label").map(e => e.text());
        expect(names).toContain("own");
        expect(names).not.toContain("addon");
    });

    it("does not offer unassigned add-on labels from recent, even one of unknown owner", () => {
        const names = mountList({recent: true}).findAll(".label").map(e => e.text());
        expect(names).not.toContain("addon-recent");
    });

    it("keeps an add-on label that is already assigned (so it can be inspected)", () => {
        const w = mountList({inBookmark: true});
        expect(w.findAll(".label").map(e => e.text())).toContain("assigned-addon");
    });
});
