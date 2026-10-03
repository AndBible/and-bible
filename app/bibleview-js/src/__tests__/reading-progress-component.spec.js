/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import {describe, it, expect} from "vitest";
import ReadingProgress from "@/components/ReadingProgress.vue";

describe("ReadingProgress.vue", () => {
    it("renders the text when provided", () => {
        const wrapper = shallowMount(ReadingProgress, {props: {text: "47% · page 142/~300"}});
        expect(wrapper.find(".reading-progress").exists()).toBe(true);
        expect(wrapper.text()).toContain("47% · page 142/~300");
    });
    it("renders nothing when text is null", () => {
        const wrapper = shallowMount(ReadingProgress, {props: {text: null}});
        expect(wrapper.find(".reading-progress").exists()).toBe(false);
    });
});
