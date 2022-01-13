/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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


import {shallowMount} from "@vue/test-utils";
import Verse from "@/components/OSIS/Verse";
import {ref} from "@vue/reactivity";
import {arrayLeq, mapFrom, rangeInside, rangesOverlap, setFrom} from "@/utils";
import {useBookmarks, useGlobalBookmarks} from "@/composables/bookmarks";
import {useStrings} from "@/composables/strings";
import {useConfig} from "@/composables/config";
import {useVerseHighlight} from "@/composables/verse-highlight";
window.bibleViewDebug = {}

describe("Verse.vue", () => {
    let wrapper;
    beforeAll(() => {
        const bookmarkLabels = mapFrom([
            {
                id: 0,
                style: {color: [255, 0, 0, 255]}
            },
            {
                id: 1,
                style: {color: [255, 0, 0, 255]}
            },
            {
                id: 2,
                style: {color: [255, 0, 0, 255]}
            },
        ], v => v.id, v => v.style);

        const bookmarks = {
            bookmarks: [
                {
                    id: 1,
                    ordinalRange: [99, 101],
                    ordinalAndOffsetRange: [[10, 10], [15, 3]],
                    labels: [0],
                },
                {
                    id: 2,
                    ordinalRange: [99, 101],
                    ordinalAndOffsetRange: [[10, 10], [15, 5]],
                    labels: [1],
                },
                {
                    id: 3,
                    ordinalRange: [99, 101],
                    ordinalAndOffsetRange: [[10, 15], [15, 3]],
                    labels: [0, 1, 2],
                },
            ], bookmarkLabels};
        const fragmentInfo = {fragmentKey: "ASDF", elementCount: ref(0)};
        const config = useConfig();
        const strings = useStrings();

        const provide = {
            bookmarks,
            fragmentInfo,
            config,
            strings,
            verseHighlight: useVerseHighlight(),
        }
        wrapper = shallowMount(Verse,
            {
                props: {
                    osisID: "Matt.1.1",
                    verseOrdinal: "100",
                },
                global: {
                    provide
                }
            })

    })
    it("Test that leq works", () => {
        expect(arrayLeq([0, 0], [0, 1])).toBe(true);
        expect(arrayLeq([0, 0], [1, 1])).toBe(true);
        expect(arrayLeq([0, 1], [1, 1])).toBe(true);
        expect(arrayLeq([1, 1], [1, 1])).toBe(true);
        expect(arrayLeq([1, 1], [1, 0])).toBe(false);
        expect(arrayLeq([1, 1], [0, 0])).toBe(false);
        expect(arrayLeq([1, 0], [0, 0])).toBe(false);
        expect(arrayLeq([1, 0], [0, 0])).toBe(false);
    });
    xit("Test styleranges", () => {
        const result = [
            {
                "bookmarks": setFrom(1, 2, 3),
                "ordinalAndOffsetRange": [[10, 10], [10, 15]],
                "labels": setFrom(0, 1, 2),
            },
            {"bookmarks": setFrom(1, 2, 3), "ordinalAndOffsetRange": [[10, 15], [15, 3]], "labels": setFrom(0, 1, 2)},
            {"bookmarks": setFrom(1, 2, 3), "ordinalAndOffsetRange": [[15, 3], [15, 5]], "labels": setFrom(0, 1, 2)}]
        expect(wrapper.vm.styleRanges.map(({bookmarks, ordinalAndOffsetRange, labels}) => ({bookmarks, ordinalAndOffsetRange, labels}))).toStrictEqual(result);
    });
})

const testBookmarkLabels =  [
    {
        id: 0,
        style: {color: [255, 0, 0, 255]}
    },
    {
        id: 1,
        style: {color: [255, 0, 0, 255]}
    },
    {
        id: 2,
        style: {color: [255, 0, 0, 255]}
    }];


describe ("bookmark test", () => {
    xit("Test styleranges 2", async () => {
        const globalBookmarks = useGlobalBookmarks();
        const {updateBookmarks, updateBookmarkLabels} = globalBookmarks;
        const {config} = useConfig()
        const {styleRanges} = useBookmarks("frag-key-1", [0,100], globalBookmarks, "KJV",  {value: true}, {adjustedColor: () => null}, config);
        updateBookmarkLabels(testBookmarkLabels);
        const ordinalRange = [0,1];
        updateBookmarks({
            id: 1,
            bookInitials: "KJV",
            ordinalRange,
            offsetRange: [1, 2],
            labels: [0]
        })
        expect(styleRanges.value).toEqual([{bookmarks: [1], labels: [0], ordinalAndOffsetRange: [[1,0], [2,0]]}]);

        updateBookmarks({
            id: 2,
            ordinalRange,
            offsetRange: [3, 4],
            labels: [0]
        })
        expect(styleRanges.value).toEqual([
            {bookmarks: [1], labels: [0], ordinalAndOffsetRange: [[1,0], [2,0]]},
            {bookmarks: [2], labels: [0], ordinalAndOffsetRange: [[3,0], [4,0]]},
        ]);
    });
    it("Ranges overlap", () => {
        expect(rangesOverlap([[3, 0], [4,0]], [[1,0], [2,0]])).toBe(false);

        expect(rangesOverlap([[1, 0], [4,0]], [[1,0], [2,0]])).toBe(true);

        expect(rangesOverlap([[1, 0], [2,0]], [[3,0], [4,0]])).toBe(false);

        expect(rangesOverlap([[1, 0], [2,0]], [[2,0], [4,0]], {inclusive: false})).toBe(false);
        expect(rangesOverlap([[1, 0], [2,0]], [[2,0], [4,0]], {inclusive: true})).toBe(true);
    });

    it("Ranges overlap 1", () => expect(rangesOverlap([[1, 0], [2,null]], [[1,0], [2,null]])).toBe(true));
    it("Ranges overlap 2", () => expect(rangesOverlap([[1, 0], [2,null]], [[2,0], [2,null]])).toBe(true));
    it("Ranges overlap 3", () => expect(rangesOverlap([[3, 0], [3,null]], [[2,0], [2,null]])).toBe(false));
    it("Ranges overlap 4", () => expect(rangesOverlap([[3, 0], [3,10]], [[3,9], [3,null]])).toBe(true));
    it("Ranges overlap 5", () => expect(rangesOverlap([[3, 0], [3,10]], [[3,10], [3,null]])).toBe(false));
    it("Ranges overlap 6", () => expect(rangesOverlap([[3, 0], [3,10]], [[3,11], [3,null]])).toBe(false));

    it("Range inside 1", () => expect(rangeInside([[3, 11], [3,null]], [[3,10], [4, 0]])).toBe(true));
    it("Range inside 2", () => expect(rangeInside([[3, 0], [3,null]], [[3, 0], [4, 0]])).toBe(true));
    it("Range inside 3", () => expect(rangeInside([[314, 53], [314,null]], [[314, 53], [315, 59]])).toBe(true));

});
