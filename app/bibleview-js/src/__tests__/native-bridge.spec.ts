import {describe, it, expect, beforeEach, vi} from "vitest";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {installNativeBridge} from "@/bridge/native-bridge";

type AnyWindow = Omit<Window, "android"> & Record<string, any>;
const w = window as AnyWindow;

function reset() {
    delete w.android;
    delete w.androidBridge;
    delete w.webkit;
    delete w.__activeLanguages__;
}

describe("native bridge", () => {
    beforeEach(reset);

    it("posts JSON text to androidBridge on Android", () => {
        const posted: string[] = [];
        w.androidBridge = {postMessage: (m: string) => posted.push(m), getActiveLanguages: () => '["fi"]'};
        installNativeBridge(w);
        w.android.addBookmark("KJV", 1, 2, false);
        expect(posted).toEqual(['{"method":"addBookmark","args":["KJV",1,2,false]}']);
    });

    it("posts objects to webkit.messageHandlers.bridge on iOS", () => {
        const posted: unknown[] = [];
        w.webkit = {messageHandlers: {bridge: {postMessage: (m: unknown) => posted.push(m)}}};
        installNativeBridge(w);
        w.android.scrolledToOrdinal("KJVA:Eph.2", 36300);
        w.android.setClientReady();
        expect(posted).toEqual([
            {method: "scrolledToOrdinal", args: ["KJVA:Eph.2", 36300]},
            {method: "setClientReady", args: []},
        ]);
    });

    it("prefers androidBridge when both transports exist", () => {
        const android: string[] = [];
        const ios: unknown[] = [];
        w.androidBridge = {postMessage: (m: string) => android.push(m), getActiveLanguages: () => "[]"};
        w.webkit = {messageHandlers: {bridge: {postMessage: (m: unknown) => ios.push(m)}}};
        installNativeBridge(w);
        w.android.toast("x");
        expect(android).toHaveLength(1);
        expect(ios).toHaveLength(0);
    });

    it("sends undefined arguments as null", () => {
        const posted: any[] = [];
        w.webkit = {messageHandlers: {bridge: {postMessage: (m: unknown) => posted.push(m)}}};
        installNativeBridge(w);
        w.android.helpDialog("c", undefined);
        expect(posted[0].args).toEqual(["c", null]);
    });

    it("answers getActiveLanguages synchronously from androidBridge", () => {
        const posted: string[] = [];
        w.androidBridge = {postMessage: (m: string) => posted.push(m), getActiveLanguages: () => '["fi","en"]'};
        installNativeBridge(w);
        expect(JSON.parse(w.android.getActiveLanguages())).toEqual(["fi", "en"]);
        expect(posted).toEqual([]);
    });

    it("answers getActiveLanguages from the injected global on iOS, defaulting to english", () => {
        w.webkit = {messageHandlers: {bridge: {postMessage: () => {}}}};
        installNativeBridge(w);
        expect(JSON.parse(w.android.getActiveLanguages())).toEqual(["en"]);
        w.__activeLanguages__ = '["de"]';
        expect(JSON.parse(w.android.getActiveLanguages())).toEqual(["de"]);
    });

    it("noTransportDoesNotThrowOrRecurse", () => {
        installNativeBridge(w);
        const original = console.warn;
        const spy = vi.fn((...a: unknown[]) => { if (spy.mock.calls.length < 5) w.android.console("warn", String(a)); });
        console.warn = spy;
        try {
            expect(() => w.android.toast("x")).not.toThrow();
        } finally {
            console.warn = original;
        }
        expect(spy).not.toHaveBeenCalled();
    });

    it("forwards window errors as console calls", () => {
        const posted: any[] = [];
        w.webkit = {messageHandlers: {bridge: {postMessage: (m: unknown) => posted.push(m)}}};
        installNativeBridge(w);
        window.dispatchEvent(new ErrorEvent("error", {message: "boom", filename: "a.js", lineno: 3}));
        expect(posted).toContainEqual({method: "console", args: ["error", "boom (a.js:3)"]});
    });

    it("does not replace an existing window.android", () => {
        const existing = {toast: () => {}};
        w.android = existing;
        installNativeBridge(w);
        expect(w.android).toBe(existing);
    });

    it("is not mistaken for a thenable, serialisable or iterable object", () => {
        installNativeBridge(w);
        expect(w.android.then).toBeUndefined();
        expect(w.android.toJSON).toBeUndefined();
        expect(w.android[Symbol.iterator]).toBeUndefined();
        expect(JSON.stringify({a: w.android})).toBe('{"a":{}}');
    });

    it("is the first import of main.ts", () => {
        const main = readFileSync(resolve(__dirname, "../main.ts"), "utf8");
        const firstImport = main.split("\n").find(l => l.startsWith("import "));
        expect(firstImport).toBe('import "@/bridge/native-bridge";');
    });
});
