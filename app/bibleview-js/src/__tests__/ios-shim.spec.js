import {describe, it, expect, beforeEach} from "vitest";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";

const iosDir = resolve(__dirname, "../../ios");
const shimSource = readFileSync(resolve(iosDir, "ios-shim.js"), "utf8");

function install() {
    const posted = [];
    window.webkit = {messageHandlers: {bridge: {postMessage: (m) => posted.push(m)}}};
    delete window.android;
    new Function(shimSource)();
    return posted;
}

describe("ios-shim", () => {
    let posted;
    beforeEach(() => { posted = install(); });

    it("forwards any android method as {method, args}", () => {
        window.android.scrolledToOrdinal("KJVA:Eph.2", 36300);
        window.android.setClientReady();
        expect(posted).toEqual([
            {method: "scrolledToOrdinal", args: ["KJVA:Eph.2", 36300]},
            {method: "setClientReady", args: []},
        ]);
    });

    it("getActiveLanguagesReturnsInjectedJson", () => {
        window.__activeLanguages__ = '["en","fi"]';
        expect(JSON.parse(window.android.getActiveLanguages())).toEqual(["en", "fi"]);
        expect(posted).toEqual([]);
    });

    it("getActiveLanguages defaults to english", () => {
        delete window.__activeLanguages__;
        expect(JSON.parse(window.android.getActiveLanguages())).toEqual(["en"]);
    });

    it("forwards window errors", () => {
        window.dispatchEvent(new ErrorEvent("error", {message: "boom", filename: "a.js", lineno: 3}));
        expect(posted).toContainEqual({method: "console", args: ["error", "boom (a.js:3)"]});
    });

    it("is idempotent when injected twice", () => {
        new Function(shimSource)();
        window.android.setClientReady();
        expect(posted.filter(m => m.method === "setClientReady")).toHaveLength(1);
    });
});

describe("poc fixture", () => {
    it("is in sync with the generator input", () => {
        const doc = JSON.parse(readFileSync(resolve(iosDir, "poc-document.json"), "utf8"));
        const xml = readFileSync(resolve(__dirname, "testdata/eph.2-kjva.xml"), "utf8");
        expect(doc.type).toBe("bible");
        expect(doc.osisFragment.xml).toBe(xml);
        expect(doc.ordinalRange[0]).toBe(36294);
        expect(doc.osisFragment.ordinalRange).toEqual(doc.ordinalRange);
    });
});
