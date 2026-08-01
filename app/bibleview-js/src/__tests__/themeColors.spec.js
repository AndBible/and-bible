import {describe, expect, it, beforeEach} from "vitest";
import {applyThemeColors} from "@/composables/config";

describe("applyThemeColors", () => {
    beforeEach(() => {
        document.documentElement.removeAttribute("style");
    });

    it("sets one custom property per role", () => {
        applyThemeColors({
            primary: "#FF8000", onPrimary: "#FFFFFF",
            primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800",
            secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10",
        });
        const style = document.documentElement.style;
        expect(style.getPropertyValue("--ab-primary")).toBe("#FF8000");
        expect(style.getPropertyValue("--ab-primary-container")).toBe("#FFDCC0");
        expect(style.getPropertyValue("--ab-on-primary-container")).toBe("#2A1800");
    });

    it("removes every property when the payload is null, so the CSS fallbacks apply", () => {
        applyThemeColors({
            primary: "#FF8000", onPrimary: "#FFFFFF",
            primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800",
            secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10",
        });
        applyThemeColors(null);
        expect(document.documentElement.style.getPropertyValue("--ab-primary")).toBe("");
        expect(document.documentElement.style.getPropertyValue("--ab-primary-container")).toBe("");
    });
});
