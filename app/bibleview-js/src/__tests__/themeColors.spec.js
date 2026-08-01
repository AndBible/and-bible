import {describe, expect, it, beforeEach} from "vitest";
import {readFileSync} from "fs";
import {dirname, join} from "path";
import {fileURLToPath} from "url";
import {applyThemeColors} from "@/composables/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

// Every `var(--ab-*, <fallback>)` declaration the A/B batch 4b workspace-theme work introduced in
// the Vue/BibleView chrome, in the order it appears in each source file. This is the single place
// that records what each fallback MUST be (today's pre-existing literal, per spec §7) — if you add,
// remove or edit a themed declaration in one of these files, update this list in the same commit,
// or the "keeps the pre-existing literal as every var() fallback" test below will fail.
const EXPECTED_FALLBACKS = {
    "../common.scss": [
        // --modal-grey (used by $modal-header-background-color and $button-grey)
        {property: "--ab-primary-container", fallback: "rgb(172,172,172)"},
        // $night-modal-header-background-color
        {property: "--ab-primary-container", fallback: "rgba(69,69,69,1)"},
    ],
    "../components/modals/ModalDialog.vue": [
        // .modal-footer background-color (day)
        {property: "--ab-secondary-container", fallback: "#acacac"},
        // .modal-footer background-color (.night &)
        {property: "--ab-secondary-container", fallback: "#454545"},
    ],
    "../components/tabs/TabNavigation.vue": [
        // .tab-button:hover:not(:disabled) — day
        {property: "--ab-on-secondary-container", fallback: "#007bff"},
        {property: "--ab-secondary-container", fallback: "#f8f9fa"},
        // .tab-button:hover:not(:disabled) .night &
        {property: "--ab-on-secondary-container", fallback: "#1e90ff"},
        {property: "--ab-secondary-container", fallback: "#333"},
        // .tab-button.active — day
        {property: "--ab-on-secondary-container", fallback: "#007bff"},
        {property: "--ab-secondary-container", fallback: "#007bff"},
        // .tab-button.active .night &
        {property: "--ab-on-secondary-container", fallback: "#1e90ff"},
        {property: "--ab-secondary-container", fallback: "#1e90ff"},
    ],
};

// Scans a source file's raw text for every `var(--ab-…, <fallback>)` occurrence, in appearance
// order. This is how the assertions below get their "actual" list, rather than transcribing line
// numbers by hand: the file itself is the source of truth for what declarations currently exist,
// EXPECTED_FALLBACKS above is the source of truth for what each fallback must be.
function extractVarFallbacks(source) {
    const re = /var\(\s*(--ab-[a-zA-Z-]+)\s*,\s*((?:[^()]+|\([^()]*\))+)\)/g;
    const matches = [];
    let match;
    while ((match = re.exec(source)) !== null) {
        matches.push({property: match[1], fallback: match[2].trim()});
    }
    return matches;
}

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

describe("var() fallbacks in the themed chrome", () => {
    it.each(Object.entries(EXPECTED_FALLBACKS))(
        "keeps the pre-existing literal as every var() fallback in %s",
        (relativePath, expected) => {
            const source = readFileSync(join(__dirname, relativePath), "utf8");
            expect(extractVarFallbacks(source)).toEqual(expected);
        }
    );
});
