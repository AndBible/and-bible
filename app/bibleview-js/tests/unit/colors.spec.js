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


import {adjustedColor, colorLightness, mixColors} from "@/utils";
import Color from "color";

const c = Color("#00EDFF");
const m = Color("#FF00AB");
const y = Color("#FFED00");
const r = Color("red");
const col = Color(-1282938);

describe("myMixColors test", () => {
    it("test1", () => expect(mixColors(y, c).hex()).toEqual("#89FF89"));
    it("test2", () => expect(mixColors(y, c, m).hex()).toEqual("#FFEDD5"));
    it("test y = (y,y)", () => expect(mixColors(y, y).hex()).toEqual(y.hex()));
    it("test y = (y,y,y)", () => expect(mixColors(y, y, y).hex()).toEqual(y.hex()));
    it("test3a", () => expect(mixColors(y, r).hex()).toEqual("#FF7600"));
    it("test3b", () => expect(mixColors(y, y, r, r).hex()).toEqual("#FF7600"));
    it("test4", () => expect(mixColors(y, y, r).hex()).toEqual("#FF9E00"));
    it("test5", () => expect(mixColors(y, y, y, r).hex()).toEqual("#FFB100"));
    it("colorDarkness 1", () => expect(colorLightness(Color("white"))).toEqual(1));
    it("colorDarkness 2", () => expect(colorLightness(Color("black"))).toEqual(0));
    it("White color as int", () => expect(Color("white").rgbNumber()).toEqual(16777215));
    it("Black color as int", () => expect(Color("black").rgbNumber()).toEqual(0));
    it("Test ", () => expect(col.hex()).toEqual("#EC6C86"));
    it("Test 1 ", () => expect(col.lighten(0.4).hex()).toEqual("#FCE6EA"));
    it("Test 1 ", () => expect(Color("#FCE6EA").lighten(0.4).hex()).toEqual("#FFFFFF"));
    it("Test 2 ", () => expect(col.lighten(0.6).hex()).toEqual("#FFFFFF"));
    it("Adjusted color 1 ", () => expect(adjustedColor(-1282938, -0.6).hex()).toEqual("#FEF5F7"));
});
