import {describe, expect, it} from "vitest";
import {cssFontFamily} from "@/utils";

describe("cssFontFamily", () => {
    it("keeps CSS generic keywords bare", () => {
        for (const g of ["serif", "sans-serif", "monospace", "cursive", "fantasy", "system-ui"]) {
            expect(cssFontFamily(g)).toBe(g);
        }
    });

    it("leaves plain names bare and quotes digit-leading and special-character ones", () => {
        expect(cssFontFamily("Study Mono")).toBe("Study Mono");
        expect(cssFontFamily("default")).toBe('"default"');
        expect(cssFontFamily("7 Segment")).toBe('"7 Segment"');
        expect(cssFontFamily("Foo;bar}")).toBe('"Foo;bar}"');
    });

    it("escapes backslashes, double quotes and newlines", () => {
        expect(cssFontFamily('A"B\\C')).toBe('"A\\"B\\\\C"');
        expect(cssFontFamily("A\nB")).toBe('"A\\a B"');
    });

    it("passes through lists and already quoted strings", () => {
        expect(cssFontFamily("Foo, serif")).toBe("Foo, serif");
        expect(cssFontFamily("'Foo Bar'")).toBe("'Foo Bar'");
    });

    it("produces values the browser accepts for a digit-leading family", () => {
        const el = document.createElement("div");
        el.style.fontFamily = "7 Segment";
        expect(el.style.fontFamily).toBe("");
        el.style.fontFamily = cssFontFamily("7 Segment");
        expect(el.style.fontFamily).not.toBe("");
    });
});
