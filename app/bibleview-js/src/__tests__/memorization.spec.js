import {describe, expect, it} from "vitest";
import {groupConsecutive} from "@/composables/memorization";

describe("groupConsecutive", () => {
    it("returns empty array for empty input", () => {
        expect(groupConsecutive([])).toEqual([]);
    });

    it("groups a single ordinal", () => {
        expect(groupConsecutive([5])).toEqual([[5, 5]]);
    });

    it("groups consecutive ordinals into one range", () => {
        expect(groupConsecutive([3, 4, 5, 6])).toEqual([[3, 6]]);
    });

    it("splits non-consecutive ordinals into separate ranges", () => {
        expect(groupConsecutive([1, 2, 5, 6, 7, 10])).toEqual([
            [1, 2],
            [5, 7],
            [10, 10],
        ]);
    });

    it("handles unsorted input", () => {
        expect(groupConsecutive([7, 3, 5, 4, 6])).toEqual([[3, 7]]);
    });

    it("handles duplicates", () => {
        expect(groupConsecutive([3, 3, 4, 4, 5])).toEqual([[3, 5]]);
    });

    it("handles single-element gaps", () => {
        expect(groupConsecutive([1, 3, 5])).toEqual([
            [1, 1],
            [3, 3],
            [5, 5],
        ]);
    });

    it("handles large consecutive range", () => {
        const ordinals = Array.from({length: 100}, (_, i) => i + 1);
        expect(groupConsecutive(ordinals)).toEqual([[1, 100]]);
    });

    it("handles multiple separate ranges with unsorted input", () => {
        expect(groupConsecutive([20, 10, 11, 12, 21, 30, 31])).toEqual([
            [10, 12],
            [20, 21],
            [30, 31],
        ]);
    });
});
