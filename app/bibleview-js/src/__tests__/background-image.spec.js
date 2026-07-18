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

import {describe, it, expect} from "vitest";
import {backgroundImageLayer} from "@/code/background-image";

const colors = {
    dayBackgroundImage: "BGIMG_day",
    nightBackgroundImage: "BGIMG_night",
    dayBackgroundImageOpacity: 80,
    nightBackgroundImageOpacity: 40,
};
const ctx = {nightMode: false, monochromeMode: false, einkMode: false};

describe("backgroundImageLayer", () => {
    it("returns the day image url and opacity in day mode", () => {
        const r = backgroundImageLayer(colors, ctx);
        expect(r).toEqual({url: "/background/BGIMG_day", opacity: 0.8});
    });

    it("returns the night image in night mode", () => {
        const r = backgroundImageLayer(colors, {...ctx, nightMode: true});
        expect(r).toEqual({url: "/background/BGIMG_night", opacity: 0.4});
    });

    it("returns null in monochrome mode", () => {
        expect(backgroundImageLayer(colors, {...ctx, monochromeMode: true})).toBeNull();
    });

    it("returns null in e-ink mode", () => {
        expect(backgroundImageLayer(colors, {...ctx, einkMode: true})).toBeNull();
    });

    it("returns null when no image is set for the current mode", () => {
        expect(backgroundImageLayer({...colors, dayBackgroundImage: null}, ctx)).toBeNull();
        expect(backgroundImageLayer({...colors, dayBackgroundImage: ""}, ctx)).toBeNull();
    });

    it("defaults opacity to 1 when opacity is missing", () => {
        const r = backgroundImageLayer({...colors, dayBackgroundImageOpacity: undefined}, ctx);
        expect(r.opacity).toBe(1);
    });

    it("preserves an explicit opacity of 0 (does not default to full)", () => {
        const r = backgroundImageLayer({...colors, dayBackgroundImageOpacity: 0}, ctx);
        expect(r.opacity).toBe(0);
    });

    it("url-encodes the module initials", () => {
        const r = backgroundImageLayer({...colors, dayBackgroundImage: "BG IMG"}, ctx);
        expect(r.url).toBe("/background/BG%20IMG");
    });
});
