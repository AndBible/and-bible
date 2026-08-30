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
        // Round 17c — --primary-color: a FOREGROUND accent used by the memorize components
        {property: "--ab-primary", fallback: "#bdbdbd"},
        // Round 17c — the three tokens, day
        {property: "--ab-primary-rgb", fallback: "0, 0, 0"},
        {property: "--ab-primary", fallback: "#666"},
        {property: "--ab-secondary-container", fallback: "#ccc"},
        // Round 17c — the three tokens, .night
        {property: "--ab-primary-rgb", fallback: "255, 255, 255"},
        {property: "--ab-primary", fallback: "#999"},
        {property: "--ab-secondary-container", fallback: "#555"},
        // Round 17c — .button.light: the container IS the background, the on- role is its text
        {property: "--ab-secondary-container", fallback: "#bdbdbd"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        // Round 17c — .button.light .night &
        {property: "--ab-secondary-container", fallback: "#616161"},
        {property: "--ab-on-secondary-container", fallback: "#bdbdbd"},
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
        // Round 17c — .tab-navigation's bottom rule: a hairline SURFACE under the strip, not a mark
        {property: "--ab-secondary-container", fallback: "#eee"},
        // Round 17c — .tab-navigation .night &
        {property: "--ab-secondary-container", fallback: "#444"},
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

// Round 17c. The three tokens through which every file OUTSIDE the EXPECTED_FALLBACKS map reaches a
// theme role. Recorded here as exact text because jsdom performs no var() substitution — verified:
// `getComputedStyle(root).getPropertyValue("--a")` on `:root { --a: var(--seed, 0, 0, 0) }` returns
// the literal string "var(--seed,0,0,0)". A "resolves to…" assertion here could not fail, so the
// resolved behaviour is verified on the device over CDP instead, and this file guards the text.
const EXPECTED_TOKENS = {
    "--accent-rgb": {day: "var(--ab-primary-rgb, 0, 0, 0)", night: "var(--ab-primary-rgb, 255, 255, 255)"},
    "--accent-mark": {day: "var(--ab-primary, #666)", night: "var(--ab-primary, #999)"},
    "--memorize-mask": {day: "var(--ab-secondary-container, #ccc)", night: "var(--ab-secondary-container, #555)"},
};

// Round 17c. Every `rgba(var(--accent-rgb), <alpha>)` site, per file, in source order, with the
// alpha the site had BEFORE this round. The token supplies the tone; this map is what stops the
// tone change from also becoming an opacity change. Add a site → add its entry here.
const EXPECTED_TINTS = {
    "../common.scss": [
        // .journal-button — the study pad's ⋯ menu, add-entry, edit-notes, indent, delete, drag
        {token: "--accent-rgb", alpha: "0.5"},
        // .journal-button .night &
        {token: "--accent-rgb", alpha: "0.6"},
        // .isHighlighted — one or more selected verses (Reference.vue @extends this)
        {token: "--accent-rgb", alpha: "0.1"},
        // .isHighlighted .night &
        {token: "--accent-rgb", alpha: "0.3"},
    ],
    "../editor-common.scss": [
        // .pell-actionbar — the toolbar's icon colour, which .pell-button inherits
        {token: "--accent-rgb", alpha: "0.6"},
        // .pell-actionbar .night &
        {token: "--accent-rgb", alpha: "0.5"},
    ],
    "../components/memorize/WordScramble.vue": [
        // .preview — the wash behind an unsolved scramble. Day and night were the same alpha over
        // opposite neutrals, which is exactly what the token carries, so the .night & branch is gone.
        {token: "--accent-rgb", alpha: "0.03"},
    ],
    "../components/memorize/WordOrder.vue": [
        // the slot preview's wash — same story as WordScramble's .preview
        {token: "--accent-rgb", alpha: "0.03"},
    ],
};

// Round 17c. Which files may consume which token. The tint map above already covers --accent-rgb's
// alphas; this is what catches a token being used somewhere it was never designed for — an opaque
// --accent-mark on a text label, say, which rule 1 of the spec (§3) forbids.
// NOTE (deviation from the task-7 brief, flagged for review): common.scss only DEFINES
// --memorize-mask (`--memorize-mask: var(--ab-secondary-container, ...)`) — it never
// consumes it via `var(--memorize-mask)` (unlike --accent-mark, which it both defines
// and consumes at its `color: var(--accent-mark);` icon-button rule). common.scss's own
// unscoped `.memorize-word.blurred` rule (still `rgba(0,0,0,0.2)`) is dead CSS: WordBlur.vue
// is the only place "blurred" is ever combined with "memorize-word", and its scoped style
// always outranks the unscoped one, so that block never paints. The brief listed "common.scss"
// as an allowed --memorize-mask consumer, mirroring --accent-mark's entry, but that doesn't
// hold under the registry test's literal var()-usage definition. Left out here rather than
// silently forcing a match.
const TOKEN_CONSUMERS = {
    "--accent-mark": ["common.scss"],
    "--memorize-mask": ["components/memorize/WordBlur.vue"],
};

function extractTints(source) {
    const re = /rgba\(\s*var\(\s*(--accent-rgb)\s*\)\s*,\s*([0-9.]+)\s*\)/g;
    const matches = [];
    let match;
    while ((match = re.exec(source)) !== null) matches.push({token: match[1], alpha: match[2]});
    return matches;
}

function tokenDeclarations(source, token) {
    const re = new RegExp(`^\\s*${token}:\\s*(.+?);`, "gm");
    const values = [];
    let match;
    while ((match = re.exec(source)) !== null) values.push(match[1].trim());
    return values;
}

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

    it("publishes the accent's channels as --ab-primary-rgb, so CSS can tint with an alpha", () => {
        applyThemeColors({
            primary: "#FF8000", onPrimary: "#FFFFFF",
            primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800",
            secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10",
        });
        expect(document.documentElement.style.getPropertyValue("--ab-primary-rgb")).toBe("255, 128, 0");
    });

    it("removes --ab-primary-rgb with the rest when the payload is null", () => {
        applyThemeColors({
            primary: "#FF8000", onPrimary: "#FFFFFF",
            primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800",
            secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10",
        });
        applyThemeColors(null);
        expect(document.documentElement.style.getPropertyValue("--ab-primary-rgb")).toBe("");
    });

    // An invalid custom-property value does NOT fall back to the var() fallback — it makes the whole
    // declaration invalid at computed-value time, which would leave every tinted mark unpainted
    // rather than grey. So a primary the colour library cannot parse must leave the channels unset
    // while the six hex roles are still applied.
    it("leaves --ab-primary-rgb unset when primary cannot be parsed, and still applies the roles", () => {
        applyThemeColors({
            primary: "not-a-colour", onPrimary: "#FFFFFF",
            primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800",
            secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10",
        });
        const style = document.documentElement.style;
        expect(style.getPropertyValue("--ab-primary-rgb")).toBe("");
        expect(style.getPropertyValue("--ab-primary")).toBe("not-a-colour");
        expect(style.getPropertyValue("--ab-primary-container")).toBe("#FFDCC0");
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

describe("theme tokens", () => {
    const commonScss = () => readFileSync(join(__dirname, "../common.scss"), "utf8");

    it.each(Object.entries(EXPECTED_TOKENS))(
        "declares %s exactly once for day and once for night, with the pre-round literal as the fallback",
        (token, expected) => {
            expect(tokenDeclarations(commonScss(), token)).toEqual([expected.day, expected.night]);
        }
    );

    it("declares the tokens only in common.scss", () => {
        const srcDir = join(__dirname, "..");
        const definers = readdirSync(srcDir, {recursive: true, encoding: "utf8"})
            .filter(p => /\.(scss|vue|ts|js)$/.test(p))
            .map(p => p.split(sep).join("/"))
            .filter(p => p !== "__tests__/themeColors.spec.js")
            .filter(p => Object.keys(EXPECTED_TOKENS)
                .some(t => new RegExp(`^\\s*${t}:`, "m").test(readFileSync(join(srcDir, p), "utf8"))))
            .sort();
        expect(definers).toEqual(["common.scss"]);
    });

    it.each(Object.entries(TOKEN_CONSUMERS))(
        "uses %s only in the files the registry lists",
        (token, expected) => {
            const srcDir = join(__dirname, "..");
            const actual = readdirSync(srcDir, {recursive: true, encoding: "utf8"})
                .filter(p => /\.(scss|vue|ts|js)$/.test(p))
                .map(p => p.split(sep).join("/"))
                .filter(p => p !== "__tests__/themeColors.spec.js")
                .filter(p => new RegExp(`var\\(\\s*${token}\\s*\\)`).test(readFileSync(join(srcDir, p), "utf8")))
                .sort();
            expect(actual).toEqual([...expected].sort());
        }
    );
});

describe("alpha-composited tints", () => {
    it.each(Object.entries(EXPECTED_TINTS))(
        "keeps the pre-round alpha at every tinted site in %s",
        (relativePath, expected) => {
            const source = readFileSync(join(__dirname, relativePath), "utf8");
            expect(extractTints(source)).toEqual(expected);
        }
    );

    it("tints only in the files this map covers", () => {
        const srcDir = join(__dirname, "..");
        const covered = Object.keys(EXPECTED_TINTS).map(p => p.replace(/^\.\.\//, "")).sort();
        const actual = readdirSync(srcDir, {recursive: true, encoding: "utf8"})
            .filter(p => /\.(scss|vue|ts|js)$/.test(p))
            .map(p => p.split(sep).join("/"))
            .filter(p => p !== "__tests__/themeColors.spec.js")
            .filter(p => /rgba\(\s*var\(\s*--accent-rgb/.test(readFileSync(join(srcDir, p), "utf8")))
            .sort();
        expect(actual).toEqual(covered);
    });
});
