import {describe, it, expect, vi} from "vitest";
import {findStrongsSwitchCallback} from "@/utils";

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
