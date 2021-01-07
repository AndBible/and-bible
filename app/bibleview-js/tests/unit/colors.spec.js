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


import {colorLightness, mixColors} from "@/utils";
import Color from "color";

const c = Color("#00EDFF");
const m = Color("#FF00AB");
const y = Color("#FFED00");
const r = Color("red");

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
});
