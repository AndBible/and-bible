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

import {useBookmarks, useGlobalBookmarks} from "@/composables/bookmarks";
import {useConfig} from "@/composables";
import {ref} from "@vue/reactivity";

describe("useBookmark tests", () => {
    let gb, b;
    let startOrd, startOff, endOrd, endOff;
    beforeEach(() => {
        window.bibleViewDebug = {}
        const {config} = useConfig();
        gb = useGlobalBookmarks(config);
        const fragmentReady = ref(true);
        b = useBookmarks(
            "fragKey",
            [10,20],
            gb,
            "KJV",
            fragmentReady,
            {adjustedColor: () => null},
            config
        );
    });

    function addBookmarkId(id, ordinalRange, offsetRange = null) {
        gb.updateBookmarks({
            id,
            ordinalRange,
            offsetRange,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
        });
    }

    function addBookmark(ordinalRange, offsetRange) {
        addBookmarkId(1, ordinalRange, offsetRange)
    }

    it("stylerange 1", () => {
        addBookmark([10, 10]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
    });
    it("stylerange 1.5", () => {
        addBookmark([9, 10]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
    });
    it("stylerange 2", () => {
        addBookmark([10, 10], [1, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        const [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 1]);
        expect([endOrd, endOff]).toEqual([10, 5]);
    });
    it("stylerange 3", () => {
        addBookmark([10, 11], null);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(1);
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
    });
    it("stylerange 4", () => {
        addBookmark([10, 11], [0, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(2);
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [10, null]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[11, 0], [11, 5]]);
    });
    it("stylerange 5", () => {
        addBookmark([9, 11], [1, 5]);
        const rs = b.styleRanges.value;
        expect(rs.length).toBe(2);
        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 0]);
        expect([endOrd, endOff]).toEqual([11, 5]);
    });
    it("stylerange 6", () => {
        addBookmarkId(1, [10, 11], null);
        addBookmarkId(2, [10, 11], [2, 5]);
        const rs = b.styleRanges.value;
        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [10, 2]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[10, 2], [10, null]]);
        expect(rs[2].ordinalAndOffsetRange).toEqual([[11, 0], [11, 5]]);
        expect(rs[3].ordinalAndOffsetRange).toEqual([[11, 5], [11, null]]);
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1,2]);
        expect(rs[3].bookmarks).toEqual([1]);
        expect(rs.length).toBe(4);

    });
    it("stylerange 7", () => {
        addBookmarkId(1, [10, 11], null);
        addBookmarkId(2, [10, 11], [0, 5]);
        const rs = b.styleRanges.value;

        expect(rs[0].bookmarks).toEqual([1,2]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1]);

        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([10, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 0]);
        expect([endOrd, endOff]).toEqual([11, 5]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[2].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([11, 5]);
        expect([endOrd, endOff]).toEqual([11, null]);
        expect(rs.length).toBe(3);

    });
    it("stylerange 8", () => {
        addBookmarkId(1, [10, 15], null);
        addBookmarkId(2, [12, 12], null);
        const rs = b.styleRanges.value;
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1,2]);
        expect(rs[2].bookmarks).toEqual([1]);

        [[startOrd, startOff], [endOrd, endOff]] = rs[0].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([10, 0]);
        expect([endOrd, endOff]).toEqual([11, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[1].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([12, 0]);
        expect([endOrd, endOff]).toEqual([12, null]);
        [[startOrd, startOff], [endOrd, endOff]] = rs[2].ordinalAndOffsetRange;
        expect([startOrd, startOff]).toEqual([13, 0]);
        expect([endOrd, endOff]).toEqual([15, null]);
        expect(rs.length).toBe(3);
    });
    it("stylerange 9", () => {
        addBookmarkId(1, [10, 15], null);
        addBookmarkId(2, [12, 12], [3,5]);
        const rs = b.styleRanges.value;
        expect(rs[0].bookmarks).toEqual([1]);
        expect(rs[1].bookmarks).toEqual([1]);
        expect(rs[2].bookmarks).toEqual([1,2]);
        expect(rs[3].bookmarks).toEqual([1]);
        expect(rs[4].bookmarks).toEqual([1]);

        expect(rs[0].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[1].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[2].ordinalAndOffsetRange).toEqual([[12, 3], [12, 5]]);
        expect(rs[3].ordinalAndOffsetRange).toEqual([[12, 5], [12, null]]);
        expect(rs[4].ordinalAndOffsetRange).toEqual([[13, 0], [15, null]]);
        expect(rs.length).toBe(5);
    });
    it("stylerange 10", () => {
        addBookmarkId(1, [10, 17], null);
        addBookmarkId(2, [12, 15], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1, 2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [15, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 5], [15, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[16, 0], [17, null]]);
        expect(rs.length).toBe(7);
    });
    it("stylerange 11", () => {
        addBookmarkId(1, [10, 16], null);
        addBookmarkId(2, [12, 14], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [13, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 0], [14, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 5], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [16, null]]);
        expect(rs.length).toBe(7);

    });

    it("stylerange 12", () => {
        addBookmarkId(1, [5, 30], null);
        addBookmarkId(2, [12, 14], [3,5]);
        const rs = b.styleRanges.value;
        let i = 0;
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[10, 0], [11, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 0], [12, 3]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[12, 3], [12, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[13, 0], [13, null]]);
        expect(rs[i].bookmarks).toEqual([1,2]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 0], [14, 5]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[14, 5], [14, null]]);
        expect(rs[i].bookmarks).toEqual([1]);
        expect(rs[i++].ordinalAndOffsetRange).toEqual([[15, 0], [20, null]]);
        expect(rs.length).toBe(7);

    });

});
