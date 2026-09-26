import {describe, it, expect, vi} from "vitest";
import {findStrongsSwitchCallback, findWordHighlightOnMenu} from "@/utils";

function fn(options, callback = () => {}) {
    return {type: "callback", callback, options};
}

describe("findStrongsSwitchCallback", () => {
    it("returns null when strongsLinkOpen is false", () => {
        const cb = vi.fn();
        const funcs = [fn({priority: 10, strongs: true}, cb)];
        expect(findStrongsSwitchCallback(funcs, false)).toBeNull();
    });

    it("returns the strongs callback when open and a strongs candidate exists", () => {
        const cb = vi.fn();
        const funcs = [
            fn({priority: 5, bookmarkId: "x"}, vi.fn()),
            fn({priority: 10, strongs: true}, cb),
        ];
        const result = findStrongsSwitchCallback(funcs, true);
        expect(result).toBe(cb);
    });

    it("returns null when open but no strongs candidate exists", () => {
        const funcs = [fn({priority: 5, bookmarkId: "x"}, vi.fn())];
        expect(findStrongsSwitchCallback(funcs, true)).toBeNull();
    });

    it("returns null when the strongs candidate has no callback", () => {
        const funcs = [fn({priority: 10, strongs: true}, null)];
        expect(findStrongsSwitchCallback(funcs, true)).toBeNull();
    });
});

describe("findWordHighlightOnMenu", () => {
    it("returns the strongs candidate's highlightWord when the setting is on", () => {
        const highlightWord = vi.fn();
        const funcs = [fn({priority: 10, strongs: true, highlightWord})];
        expect(findWordHighlightOnMenu(funcs, true)).toBe(highlightWord);
    });

    it("returns null when the setting is off, even with a strongs candidate", () => {
        const highlightWord = vi.fn();
        const funcs = [fn({priority: 10, strongs: true, highlightWord})];
        expect(findWordHighlightOnMenu(funcs, false)).toBeNull();
    });

    it("returns highlightWord even when a bookmark also sits on the word (no carve-out)", () => {
        const highlightWord = vi.fn();
        const funcs = [
            fn({priority: 5, bookmarkId: "x"}),
            fn({priority: 10, strongs: true, highlightWord}),
        ];
        expect(findWordHighlightOnMenu(funcs, true)).toBe(highlightWord);
    });

    it("returns null when there is no strongs candidate", () => {
        const funcs = [fn({priority: 5, bookmarkId: "x"})];
        expect(findWordHighlightOnMenu(funcs, true)).toBeNull();
    });

    it("returns null when the strongs candidate has no highlightWord", () => {
        const funcs = [fn({priority: 10, strongs: true})];
        expect(findWordHighlightOnMenu(funcs, true)).toBeNull();
    });
});
