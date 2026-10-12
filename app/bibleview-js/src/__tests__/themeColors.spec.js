import {describe, expect, it, beforeEach} from "vitest";
import {readFileSync, readdirSync} from "fs";
import {dirname, join, sep} from "path";
import {fileURLToPath} from "url";
import {compileString} from "sass";
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
        {property: "--ab-primary", fallback: "#474747"},
        {property: "--ab-primary", fallback: "#d5d5d5"},
        // Round 17c — --primary-color: a FOREGROUND accent used by the memorize components
        {property: "--ab-primary", fallback: "#bdbdbd"},
        // Round 17c — the three tokens, day
        {property: "--ab-primary-rgb", fallback: "0, 0, 0"},
        {property: "--ab-primary-rgb", fallback: "128, 128, 128"},
        {property: "--ab-secondary-container-rgb", fallback: "207, 207, 207"},
        {property: "--ab-on-secondary-container", fallback: "initial"},
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
    "../components/BibleView.vue": [
        {property: "--ab-primary", fallback: "rgba(196, 196, 255, 0.8)"},
        {property: "--ab-primary", fallback: "rgba(0, 0, 255, 0.6)"},
        {property: "--ab-on-primary-container", fallback: "#d5d5d5"},
    ],
    "../components/modals/AskBookmarkSettings.vue": [
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-primary-container", fallback: "#e7f1ff"},
        {property: "--ab-on-primary-container", fallback: "inherit"},
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-primary-container", fallback: "#333"},
        {property: "--ab-on-primary-container", fallback: "inherit"},
        {property: "--ab-secondary-container", fallback: "white"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#222"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-secondary-container", fallback: "#f8f9fa"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-secondary-container", fallback: "#333"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-on-primary", fallback: "white"},
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-on-primary", fallback: "white"},
        {property: "--ab-secondary-container", fallback: "white"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#222"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-secondary-container", fallback: "white"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#222"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-secondary-container", fallback: "white"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#333"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-secondary-container", fallback: "#f8f9fa"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-secondary-container", fallback: "#444"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-primary", fallback: "#1e90ff"},
        {property: "--ab-secondary-container", fallback: "white"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#333"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-secondary-container", fallback: "#f5f5f5"},
        {property: "--ab-on-secondary-container", fallback: "black"},
        {property: "--ab-secondary-container", fallback: "#444"},
        {property: "--ab-on-secondary-container", fallback: "#ccc"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-on-primary", fallback: "white"},
        {property: "--ab-primary", fallback: "#007bff"},
        {property: "--ab-primary", fallback: "#333"},
        {property: "--ab-on-primary", fallback: "#ccc"},
        {property: "--ab-primary", fallback: "#555"},
        {property: "--ab-primary", fallback: "#0056b3"},
        {property: "--ab-on-primary", fallback: "white"},
        {property: "--ab-primary", fallback: "#0056b3"},
        {property: "--ab-primary", fallback: "#444"},
        {property: "--ab-on-primary", fallback: "#ccc"},
        {property: "--ab-primary", fallback: "#0056b3"},
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
// chrome reaches the roles through common.scss's tokens — --modal-grey / --modal-grey-text /
// --icon-grey from batch 4b, and --accent-rgb / --accent-mark / --memorize-mask from round 17c —
// which is what keeps the role-of-use decision in one reviewable place, and what keeps the
// `.monochrome` overrides of those tokens effective for every consumer.
// `config.ts` is exempt: it is the writer of the properties, not a consumer.
const NON_CONSUMER_FILES = ["composables/config.ts", "__tests__/themeColors.spec.js"];

// Shared theme tokens through which every file OUTSIDE the EXPECTED_FALLBACKS map reaches a
// theme role. Recorded here as exact text because jsdom performs no var() substitution — verified:
// `getComputedStyle(root).getPropertyValue("--a")` on `:root { --a: var(--seed, 0, 0, 0) }` returns
// the literal string "var(--seed,0,0,0)". A "resolves to…" assertion here could not fail, so the
// resolved behaviour is verified on the device over CDP instead, and this file guards the text.
const EXPECTED_TOKENS = {
    "--accent-rgb": {day: "var(--ab-primary-rgb, 0, 0, 0)", night: "var(--ab-primary-rgb, 255, 255, 255)", monochrome: "0, 0, 0", monochromeNight: "255, 255, 255"},
    "--accent-rgb-mid": {day: "var(--ab-primary-rgb, 128, 128, 128)", monochrome: "128, 128, 128", monochromeNight: "128, 128, 128"},
    "--chip-rgb": {day: "var(--ab-secondary-container-rgb, 207, 207, 207)", monochrome: "207, 207, 207", monochromeNight: "207, 207, 207"},
    "--chip-text": {day: "var(--ab-on-secondary-container, initial)", monochrome: "var(--text-color)", monochromeNight: "var(--text-color)"},
    "--accent-mark": {day: "var(--ab-primary, #666)", night: "var(--ab-primary, #999)", monochrome: "black", monochromeNight: "white"},
    "--memorize-mask": {day: "var(--ab-secondary-container, #ccc)", night: "var(--ab-secondary-container, #555)", monochrome: "white", monochromeNight: "black"},
};

// Round 17c. Every `rgba(var(--accent-rgb), <alpha>)` site, per file, in source order, with the
// alpha the site had BEFORE this round. The token supplies the tone; this map is what stops the
// tone change from also becoming an opacity change. Add a site → add its entry here.
const EXPECTED_TINTS = {
    "../common.scss": [
        {token: "--accent-rgb", alpha: "0.5"},
        {token: "--accent-rgb", alpha: "0.6"},
        {token: "--accent-rgb", alpha: "0.15"},
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: ".3"},
        {token: "--accent-rgb", alpha: ".3"},
        {token: "--accent-rgb", alpha: "0.1"},
        {token: "--accent-rgb", alpha: ".1"},
        {token: "--accent-rgb", alpha: "0.15"},
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.1"},
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.05"},
        {token: "--accent-rgb", alpha: "0.05"},
        {token: "--accent-rgb", alpha: "0.2"},
        {token: "--accent-rgb", alpha: "0.2"},
    ],
    "../editor-common.scss": [
        {token: "--accent-rgb", alpha: "0.6"},
        {token: "--accent-rgb", alpha: "0.5"},
        {token: "--accent-rgb", alpha: "0.2"},
        {token: "--accent-rgb", alpha: "0.2"},
    ],
    "../components/BibleView.vue": [
        {token: "--accent-rgb", alpha: "0.15"},
        {token: "--accent-rgb", alpha: "0.15"},
        {token: "--accent-rgb", alpha: "0.5"},
        {token: "--accent-rgb", alpha: "0.5"},
        {token: "--chip-rgb", alpha: "0.71"},
    ],
    "../components/ReadingProgress.vue": [
        {token: "--chip-rgb", alpha: "0.71"},
    ],
    "../components/WholePageBookmarks.vue": [
        {token: "--accent-rgb-mid", alpha: "0.7"},
    ],
    "../components/ChapterNavigationButtons.vue": [
        {token: "--accent-rgb-mid", alpha: "0.2"},
    ],
    "../components/ButtonRow.vue": [
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.6"},
    ],
    "../components/AmbiguousActionButtons.vue": [
        {token: "--accent-rgb", alpha: "0.05"},
        {token: "--accent-rgb", alpha: "0.1"},
    ],
    "../components/documents/DocumentActionMenu.vue": [
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.6"},
    ],
    "../components/documents/StudyPadDocument.vue": [
        {token: "--accent-rgb", alpha: "0.15"},
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.2"},
        {token: "--accent-rgb", alpha: "0.2"},
        {token: "--accent-rgb", alpha: "0.2"},
        {token: "--accent-rgb", alpha: "0.2"},
    ],
    "../components/documents/BibleDocument.vue": [
        {token: "--accent-rgb", alpha: "0.3"},
        {token: "--accent-rgb", alpha: "0.3"},
    ],
    "../components/memorize/WordScramble.vue": [
        // .preview — the wash behind an unsolved scramble. Day and night were the same alpha over
        // opposite neutrals, which is exactly what the token carries, so the .night & branch is gone.
        {token: "--accent-rgb", alpha: "0.03"},
        // .settings-popup border
        {token: "--accent-rgb", alpha: "0.2"},
        // .settings-popup .night &
        {token: "--accent-rgb", alpha: "0.3"},
    ],
    "../components/memorize/WordOrder.vue": [
        // the slot preview's wash — same story as WordScramble's .preview
        {token: "--accent-rgb", alpha: "0.03"},
    ],
    "../components/memorize/WordType.vue": [
        // .tap-hint — same alpha in both modes, so the .night & branch is gone
        {token: "--accent-rgb", alpha: "0.3"},
        // .visibility-light — upcoming words, faint (night branch gone, same reason)
        {token: "--accent-rgb", alpha: "0.2"},
        // .visibility-dim — upcoming words, fainter (night branch gone)
        {token: "--accent-rgb", alpha: "0.08"},
        // .settings-popup border — the alphas DIFFER, so this one keeps its night branch
        {token: "--accent-rgb", alpha: "0.2"},
        // .settings-popup .night &
        {token: "--accent-rgb", alpha: "0.3"},
    ],
    "../components/documents/MemorizeDocument.vue": [
        // .dropdown-menu border
        {token: "--accent-rgb", alpha: "0.2"},
        // .dropdown-menu .night &
        {token: "--accent-rgb", alpha: "0.3"},
        // .menu-item:hover
        {token: "--accent-rgb", alpha: "0.05"},
        // .night &:hover
        {token: "--accent-rgb", alpha: "0.1"},
    ],
};

// Opaque and chip token consumers, alongside the alpha-site registry.
const TOKEN_CONSUMERS = {
    "--accent-rgb-mid": ["components/ChapterNavigationButtons.vue", "components/WholePageBookmarks.vue"],
    "--chip-rgb": ["components/BibleView.vue", "components/ReadingProgress.vue"],
    "--chip-text": ["components/BibleView.vue", "components/ReadingProgress.vue"],
    "--accent-mark": [
        "common.scss",
        "components/documents/MemorizeDocument.vue",
        "components/memorize/WordScramble.vue",
        "components/memorize/WordType.vue",
    ],
    "--memorize-mask": ["components/memorize/WordBlur.vue"],
};

function extractTints(source) {
    const re = /rgba\(\s*var\(\s*(--accent-rgb(?:-mid)?|--chip-rgb)\s*\)\s*,\s*([0-9.]+)\s*\)/g;
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
    const re = /var\(\s*(--ab-[a-zA-Z-]+)\s*,\s*/g;
    const matches = [];
    let match;
    while ((match = re.exec(source)) !== null) {
        const start = re.lastIndex;
        let end = start;
        let depth = 1;
        while (depth && end < source.length) {
            if (source[end] === "(") depth++;
            if (source[end] === ")") depth--;
            end++;
        }
        matches.push({property: match[1], fallback: source.slice(start, end - 1).trim()});
        re.lastIndex = end;
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
        "declares %s with mode-safe literal fallbacks and monochrome overrides",
        (token, expected) => {
            expect(tokenDeclarations(commonScss(), token)).toEqual(
                ( ["--accent-rgb-mid", "--chip-rgb", "--chip-text"].includes(token)
                    ? [expected.day, expected.monochrome, expected.monochromeNight]
                    : [expected.day, expected.night, expected.monochrome, expected.monochromeNight])
            );
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
                .filter(p => new RegExp(`var\\(\\s*${token}\\s*(?:,|\\))`).test(readFileSync(join(srcDir, p), "utf8")))
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
            .filter(p => /rgba\(\s*var\(\s*--(?:accent-rgb(?:-mid)?|chip-rgb)/.test(readFileSync(join(srcDir, p), "utf8")))
            .sort();
        expect(actual).toEqual(covered);
    });
});

// These assertions guard emitted selector declarations, not jsdom's unsupported var resolution.
// Removing a matching foreground or letting a more-specific night selector mask it breaks contrast.
function componentCss(file) {
    const source = readFileSync(join(__dirname, "../components", file), "utf8");
    const styles = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)]
        .map(m => m[1].replace(/@use[^;]+;/g, "").replace(/@extend ([^;]+);/g, "@extend $1 !optional;")).join("\n");
    return compileString(styles, {logger: {warn() {}}}).css;
}
function declarations(css, selector) {
    return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)]
        .filter(m => m[1].trim().split(/,\s*/).includes(selector))
        .map(m => m[2]).join("\n");
}
// Evaluate emitted selector matching and cascade, not CSS-variable substitution.
// Form controls live under the mode wrapper; tokens inherit from :root.
function winning(css, classes, mode = "", tag = "button") {
    css = css.replace(/\/\*[\s\S]*?\*\//g, "").replace(/@charset[^;]+;/g, "");
    const wrapper = document.createElement("div");
    wrapper.className = mode;
    const element = document.createElement(tag);
    element.className = classes;
    wrapper.append(element);
    document.body.append(wrapper);
    const result = {};
    const rank = {};
    try {
        for (const [, selectors, body] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
            for (const selector of selectors.trim().split(/,\s*/)) {
                if (!element.matches(selector)) continue;
                // :not() adds its argument specificity, not an extra pseudo-class.
                const flat = selector.replace(/:not\(([^()]*)\)/g, "$1");
                const specificity = (flat.match(/#[\w-]+/g) || []).length * 10000
                    + (flat.match(/\.[\w-]+|:(?!:)[\w-]+/g) || []).length * 100
                    + (flat.match(/(?:^|\s|>)\s*[a-z][\w-]*/gi) || []).length;
                for (const [, property, value] of body.matchAll(/([\w-]+):\s*([^;]+);/g)) {
                    if (rank[property] === undefined || specificity >= rank[property]) {
                        result[property] = value.trim();
                        rank[property] = specificity;
                    }
                }
            }
        }
        return result;
    } finally {
        wrapper.remove();
    }
}
function tokenWinner(css, token, mode) {
    // Root properties inherit until a matching mode selector overrides them.
    const root = {};
    for (const [, selectors, body] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        if (!selectors.trim().split(/,\s*/).includes(":root")) continue;
        for (const [, property, value] of body.matchAll(/([\w-]+):\s*([^;]+);/g)) root[property] = value.trim();
    }
    return winning(css, mode, "", "div")[token] ?? root[token];
}
describe("bounded theme chrome contrast and geometry", () => {
    // [selector, arm-direction toward the free end (horizontal, vertical), fillet anchor, fillet circle centre]
    it.each([
        [".top-left-corner", "to right", "to bottom", "left 3px top 3px", "100% 100%"],
        [".top-right-corner", "to left", "to bottom", "right 3px top 3px", "0 100%"],
        [".bottom-left-corner", "to right", "to top", "left 3px bottom 3px", "100% 0"],
        [".bottom-right-corner", "to left", "to top", "right 3px bottom 3px", "0 0"],
    ])("paints a filled, edge-fading active corner at %s", (selector, horizontal, vertical, anchor, centre) => {
        const css = componentCss("BibleView.vue");
        const rule = declarations(css, selector).replace(/\s+/g, " ");
        expect(rule).toContain("width: 40px");
        expect(rule).toContain("height: 40px");
        // No rounded outer corner (no gap to the screen corner) and no CSS border: the L is drawn as
        // background layers so the arms can fade out along the screen edge.
        expect(rule).not.toContain("radius");
        expect(rule).not.toContain("border");
        expect(rule).toContain(`linear-gradient(${horizontal}, currentColor 40%, var(--corner-tail))`);
        expect(rule).toContain(`linear-gradient(${vertical}, currentColor 40%, var(--corner-tail))`);
        // Concave 12px fillet that meets both arms tangentially.
        expect(rule).toContain(`radial-gradient(circle at ${centre}, transparent 11.5px, currentColor 12px) ${anchor} / 12px 12px no-repeat`);
        // The arms fade to nothing, except in monochrome and color e-ink where they stay solid.
        expect(winning(css, selector.slice(1), "", "div")["--corner-tail"]).toBe("transparent");
        expect(winning(css, selector.slice(1), "night", "div")["--corner-tail"]).toBe("transparent");
        for (const mode of ["monochrome", "monochrome night", "colorEink", "colorEink night"]) {
            expect(winning(css, selector.slice(1), mode, "div")["--corner-tail"]).toBe("currentColor");
        }
        // Paint still goes through `color` in every mode (the fillet and arms use currentColor).
        expect(winning(css, selector.slice(1), "monochrome", "div").color).toBe("black");
        expect(winning(css, selector.slice(1), "monochrome night", "div").color).toBe("white");
        expect(winning(css, selector.slice(1), "colorEink", "div").color).toBe("rgba(0, 0, 255, 0.6)");
        expect(winning(css, selector.slice(1), "", "div").color).toContain("var(--ab-primary, rgba(0, 0, 255, 0.6))");
    });
    it.each([["BibleView.vue", ".pagenumber"], ["ReadingProgress.vue", ".reading-progress"]])(
        "pairs the chip roles and restores window text on noAnimation in %s", (file, selector) => {
            const css = componentCss(file);
            expect(declarations(css, selector)).toContain("color: var(--chip-text, var(--text-color))");
            expect(declarations(css, selector)).toContain("background: rgba(var(--chip-rgb), 0.71)");
            expect(declarations(css, `.noAnimation ${selector}`)).toContain("color: var(--text-color)");
            expect(declarations(css, `.noAnimation ${selector}`)).toContain("background-color: var(--background-color)");
        }
    );
    it("pairs filled and container controls, including higher-specificity night Save and active rules", () => {
        const css = componentCss("modals/AskBookmarkSettings.vue");
        for (const selector of [".mode-toggle.active", ".night .mode-toggle.active", ".save-button", ".night .save-button", ".save-button:hover:not(:disabled)", ".night .save-button:hover:not(:disabled)"]) {
            const rule = declarations(css, selector);
            expect(rule).toMatch(/background: var\(--ab-primary,/);
            expect(rule).toMatch(/color: var\(--ab-on-primary,/);
        }
        for (const selector of [".icon-item.selected", ".night .icon-item.selected"]) {
            const rule = declarations(css, selector);
            expect(rule).toMatch(/background-color: var\(--ab-primary-container,/);
            expect(rule).toMatch(/color: var\(--ab-on-primary-container,/);
        }
        for (const selector of [".mode-toggle:hover", ".night .mode-toggle:hover", ".format-button:hover", ".night .format-button:hover", ".cancel-button", ".night .cancel-button", ".mode-select", ".content-textarea"]) {
            const rule = declarations(css, selector);
            expect(rule).toMatch(/background: var\(--ab-secondary-container,/);
            expect(rule).toMatch(/color: var\(--ab-on-secondary-container,/);
        }
    });
    it("keeps toggled modal icons on the header's foreground role", () => {
        const css = componentCss("BibleView.vue");
        expect(declarations(css, ".modal-action-button.toggled")).toContain("color: var(--ab-on-primary-container, #d5d5d5)");
        expect(declarations(css, ".monochrome .modal-action-button.toggled")).toContain("color: inherit");
    });
});

describe("secondary container channel lifecycle", () => {
    const payload = {primary: "#FF8000", onPrimary: "#FFFFFF", primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800", secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10"};
    beforeEach(() => document.documentElement.removeAttribute("style"));
    it("derives and updates container channels independently of primary", () => {
        applyThemeColors(payload);
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("243, 223, 208");
        applyThemeColors({...payload, secondaryContainer: "#123456"});
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("18, 52, 86");
        expect(document.documentElement.style.getPropertyValue("--ab-primary-rgb")).toBe("255, 128, 0");
    });
    it.each([null, "invalid", "", "rgb(no, channels, here)"])("removes stale container channels for %s", value => {
        applyThemeColors(payload);
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("243, 223, 208");
        applyThemeColors(value === null ? null : {...payload, secondaryContainer: value});
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("");
    });
    it("recovers after malformed channels and removes stale primary channels too", () => {
        applyThemeColors(payload);
        applyThemeColors({...payload, primary: "invalid", secondaryContainer: "invalid"});
        expect(document.documentElement.style.getPropertyValue("--ab-primary-rgb")).toBe("");
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("");
        applyThemeColors(payload);
        expect(document.documentElement.style.getPropertyValue("--ab-secondary-container-rgb")).toBe("243, 223, 208");
    });
});

describe("effective theme chrome cascade", () => {
    it("keeps night active text white without a payload", () => {
        expect(winning(componentCss("modals/AskBookmarkSettings.vue"), "mode-toggle active", "night").color)
            .toBe("var(--ab-on-primary, white)");
    });
    it.each(["mode-toggle", "mode-select", "content-textarea", "format-button", "cancel-button"])(
        "preserves the native day foreground for %s rather than custom window text", control => {
            expect(winning(componentCss("modals/AskBookmarkSettings.vue"), control).color)
                .toBe("var(--ab-on-secondary-container, black)");
        }
    );
    it("uses winning matching role pairs in every interactive day/night state", () => {
        const css = componentCss("modals/AskBookmarkSettings.vue");
        // matches() cannot synthesize :hover; replace it with a test state class.
        const states = css.replaceAll(":hover", ".hover");
        for (const mode of ["", "night"]) {
            for (const control of ["mode-toggle active", "mode-toggle active hover", "save-button", "save-button hover"]) {
                const rule = winning(states, control, mode);
                expect(rule.background).toMatch(/^var\(--ab-primary,/);
                expect(rule.color).toMatch(/^var\(--ab-on-primary,/);
            }
            for (const control of ["mode-toggle", "mode-toggle hover", "mode-select", "content-textarea", "format-button", "format-button hover", "cancel-button", "cancel-button hover"]) {
                const rule = winning(states, control, mode);
                expect(rule.background).toMatch(/^var\(--ab-secondary-container,/);
                expect(rule.color).toMatch(/^var\(--ab-on-secondary-container,/);
            }
            const selected = winning(states, "icon-item selected", mode);
            expect(selected["background-color"]).toMatch(/^var\(--ab-primary-container,/);
            expect(selected.color).toMatch(/^var\(--ab-on-primary-container,/);
        }
    });
    it.each(Object.entries(EXPECTED_TOKENS))("anchors %s to effective day/night/monochrome selectors", (token, expected) => {
        const css = compileString(readFileSync(join(__dirname, "../common.scss"), "utf8"), {logger: {warn() {}}}).css;
        expect(tokenWinner(css, token, "")).toBe(expected.day);
        expect(tokenWinner(css, token, "night")).toBe(expected.night ?? expected.day);
        expect(tokenWinner(css, token, "monochrome")).toBe(expected.monochrome);
        expect(tokenWinner(css, token, "monochrome night")).toBe(expected.monochromeNight);
    });
    it.each([".night .save-button", ".night .mode-toggle.active"])("rejects a late winning bad pair on %s", selector => {
        const css = componentCss("modals/AskBookmarkSettings.vue");
        const classes = selector.replace(".night ", "").replaceAll(".", " ").trim();
        const bad = winning(css + `${selector} { background: pink; color: yellow; }`, classes, "night");
        expect(bad.background).toBe("pink");
        expect(bad.color).toBe("yellow");
        expect(bad.background).not.toMatch(/^var\(--ab-primary,/);
        expect(bad.color).not.toMatch(/^var\(--ab-on-primary,/);
    });
});

describe("malformed runtime channel fields", () => {
    const valid = {primary: "#FF8000", onPrimary: "#FFFFFF", primaryContainer: "#FFDCC0", onPrimaryContainer: "#2A1800", secondaryContainer: "#F3DFD0", onSecondaryContainer: "#271A10"};
    it.each([
        ["primary", "--ab-primary-rgb", "255, 128, 0"],
        ["secondaryContainer", "--ab-secondary-container-rgb", "243, 223, 208"],
    ])("removes stale %s channels for non-string runtime values", (field, property, channels) => {
        for (const value of [null, undefined, 0, 123, false, {}, [], [1, 2, 3]]) {
            applyThemeColors(valid);
            expect(document.documentElement.style.getPropertyValue(property)).toBe(channels);
            applyThemeColors({...valid, [field]: value});
            expect(document.documentElement.style.getPropertyValue(property)).toBe("");
            applyThemeColors(valid);
            expect(document.documentElement.style.getPropertyValue(property)).toBe(channels);
        }
    });
});
