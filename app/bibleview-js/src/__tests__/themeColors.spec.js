import {describe, expect, it, beforeEach} from "vitest";
import {readFileSync, readdirSync} from "fs";
import {dirname, join, sep} from "path";
import {fileURLToPath} from "url";
import {applyThemeColors} from "@/composables/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

// Every `var(--ab-*, <fallback>)` declaration the A/B batch 4b workspace-theme work introduced in
// the Vue/BibleView chrome, in the order it appears in each source file. This is the single place
// that records what each fallback MUST be (today's pre-existing literal, per spec §7) — if you add,
// remove or edit a themed declaration in one of these files, update this list in the same commit,
// or the "keeps the pre-existing literal as every var() fallback" test below will fail.
// The role each declaration takes is NOT interchangeable, and this map is where that is recorded:
// a value used as a BACKGROUND takes a *Container role and its text takes the matching on-* role;
// a value used as a FOREGROUND (icon, spinner ring, underline) takes an accent role (--ab-primary),
// because a container role is a background tone and would be nearly invisible drawn as a mark.
const EXPECTED_FALLBACKS = {
    "../common.scss": [
        // --modal-grey — BACKGROUND of the modal header / .button
        {property: "--ab-primary-container", fallback: "rgb(172,172,172)"},
        // --modal-grey-text — TEXT drawn on --modal-grey
        {property: "--ab-on-primary-container", fallback: "white"},
        // --icon-grey — the same grey used as a FOREGROUND mark (verse-action icons, the spinner
        // ring, BookmarkModal's link icon, MultiDocument's hide/restore buttons)
        {property: "--ab-primary", fallback: "rgb(172,172,172)"},
        // $night-modal-header-background-color
        {property: "--ab-primary-container", fallback: "rgba(69,69,69,1)"},
        // $night-modal-header-foreground-color
        {property: "--ab-on-primary-container", fallback: "#e2e2e2"},
    ],
    "../components/modals/ModalDialog.vue": [
        // .modal-footer — background then its text (day)
        {property: "--ab-secondary-container", fallback: "#acacac"},
        {property: "--ab-on-secondary-container", fallback: "white"},
        // .modal-footer — background then its text (.night &)
        {property: "--ab-secondary-container", fallback: "#454545"},
        {property: "--ab-on-secondary-container", fallback: "#bdbdbd"},
    ],
    "../components/tabs/TabNavigation.vue": [
        // .tab-button:hover:not(:disabled) — day: the container IS the background, on- is its text
        {property: "--ab-on-secondary-container", fallback: "#007bff"},
        {property: "--ab-secondary-container", fallback: "#f8f9fa"},
        // .tab-button:hover:not(:disabled) .night &
        {property: "--ab-on-secondary-container", fallback: "#1e90ff"},
        {property: "--ab-secondary-container", fallback: "#333"},
        // .tab-button.active — day: label + 2px underline, both foreground marks on the bare strip
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-primary", fallback: "#007bff"},
        // .tab-button.active .night &
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-primary", fallback: "#1e90ff"},
    ],
};

// The files above are the ONLY place a `var(--ab-…)` may appear. Everything else in the themed
// chrome reaches the roles through common.scss's three tokens (--modal-grey / --modal-grey-text /
// --icon-grey), which is what keeps the role-of-use decision in one reviewable place — and what
// keeps the `.monochrome` overrides of those tokens effective for every consumer.
// `config.ts` is exempt: it is the writer of the properties, not a consumer.
const NON_CONSUMER_FILES = ["composables/config.ts", "__tests__/themeColors.spec.js"];

function sourceFilesUsingAbRoles() {
    const srcDir = join(__dirname, "..");
    return readdirSync(srcDir, {recursive: true, encoding: "utf8"})
        .filter(p => /\.(scss|vue|ts|js)$/.test(p))
        .map(p => p.split(sep).join("/"))
        .filter(p => !NON_CONSUMER_FILES.includes(p))
        .filter(p => /var\(\s*--ab-/.test(readFileSync(join(srcDir, p), "utf8")))
        .sort();
}

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

    it("declares --ab-* roles only in the files this map covers", () => {
        const covered = Object.keys(EXPECTED_FALLBACKS)
            .map(p => p.replace(/^\.\.\//, ""))
            .sort();
        expect(sourceFilesUsingAbRoles()).toEqual(covered);
    });
});
