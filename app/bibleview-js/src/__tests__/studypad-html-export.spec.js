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
import {defineComponent, h, nextTick, provide, ref} from "vue";
import {afterEach, describe, expect, it, vi} from "vitest";
import StudyPadRow from "@/components/StudyPadRow.vue";
import {useSharing} from "@/composables/sharing";
import {emit} from "@/eventbus";
import {androidKey, appSettingsKey} from "@/types/constants";

vi.mock("@/composables", () => ({
    useCommon: () => ({strings: {editTextPlaceholder: "Tap to edit text"}}),
}));

let wrapper;
afterEach(() => wrapper?.unmount());

describe("StudyPad HTML export", () => {
    it.each(["HTML", "MARKDOWN"])("omits empty %s entry placeholders but keeps written text", async contentType => {
        const shareHtml = vi.fn();
        const Host = defineComponent({
            setup() {
                const topElement = ref(null);
                useSharing({topElement, android: {shareHtml}});
                provide(androidKey, {});
                provide(appSettingsKey, {notesContentType: contentType});
                const entries = ["", "Study note"].map((text, index) => ({
                    id: `entry-${index}`, hashCode: index, type: "journal", labelId: "label-1",
                    text, contentType, orderNumber: index, indentLevel: 0,
                }));
                return () => h("div", {ref: topElement}, entries.map(journalEntry => h(StudyPadRow, {
                    key: journalEntry.id, journalEntry, label: {id: "label-1"},
                })));
            },
        });
        wrapper = mount(Host, {global: {stubs: {AreYouSure: true, ButtonRow: true}}});

        expect(wrapper.find(".placeholder").text()).toBe("Tap to edit text");
        emit("export_html");
        await vi.waitFor(() => expect(shareHtml).toHaveBeenCalledOnce());

        const html = shareHtml.mock.calls[0][0];
        expect(html).not.toContain("Tap to edit text");
        const exported = new DOMParser().parseFromString(html, "text/html");
        expect(exported.querySelector(".placeholder")).toBeNull();
        expect(exported.querySelectorAll(".studypad-text-entry")[0].textContent).toBe("");
        expect(exported.body.textContent).toContain("Study note");
        await nextTick();
        expect(wrapper.find(".placeholder").text()).toBe("Tap to edit text");
    });
});
