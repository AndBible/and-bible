import {describe, it, expect} from "vitest";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";

const iosDir = resolve(__dirname, "../../ios");

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
