import {describe, it, expect} from "vitest";
import {readFileSync} from "fs";
import {compileString} from "sass";
import {dirname, resolve} from "path";
import {fileURLToPath} from "url";
const directory = dirname(fileURLToPath(import.meta.url));

// Compile the real styles: jsdom cannot resolve inherited custom properties or SCSS selectors.
function rules(path) {
    const source = readFileSync(resolve(directory, path), "utf8");
    return compileString((path.endsWith(".vue") ? [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map(m => m[1]).join("\n") : source)
        .replace('@use "@/common.scss" as *;', readFileSync(resolve(directory, "../common.scss"), "utf8"))).css;
}
function declaration(css, selector, text) {
    const blocks = [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)];
    expect(blocks.some(([, selectors, body]) => selectors.split(",").map(s => s.trim()).includes(selector) && body.includes(text))).toBe(true);
}

describe("pure monochrome compiled styles", () => {
    it("uses ink tokens and a one-pixel frame, inverted at night", () => {
        const css = rules("../common.scss");
        declaration(css, ":root .pureMonochrome", "--primary-color: black");
        declaration(css, ":root .pureMonochrome.night", "--primary-color: white");
        declaration(css, ".pureMonochrome .mono-frame", "border-top-width: 1px");
        declaration(css, ".pureMonochrome.night .mono-frame", "border-color: white");
        declaration(css, ".pureMonochrome .icon-button.disabled", "opacity: 1");
        declaration(css, ".pureMonochrome .icon-button.disabled", "color: #808080");
    });
    it("replaces dim words and heatmaps without changing BW", () => {
        const css = rules("../components/memorize/WordType.vue");
        declaration(css, ".pureMonochrome .type-word.type-unreached.visibility-dim", "color: #808080");
        declaration(css, ".pureMonochrome .type-word.heatmap-1", "background-color: transparent");
        declaration(css, ".pureMonochrome .type-word.heatmap-3", "border-bottom-width: 3px");
        declaration(css, ".pureMonochrome.night .type-word.heatmap-1", "background-color: transparent");
        declaration(css, ".pureMonochrome.night .type-word.type-current.visibility-light", "color: #808080");
        declaration(css, ".monochrome .type-word.heatmap-1", "background-color: rgba(0, 0, 0, 0.15)");
    });
    it("uses ink for completed memorize borders and opaque hover", () => {
        const css = rules("../components/documents/MemorizeDocument.vue");
        declaration(css, ".pureMonochrome .memorize-wrapper.memorized-border", "border-color: black");
        declaration(css, ".pureMonochrome .menu-item:hover", "background: black");
    });
    it("disabled chapter controls use opaque MonoDisabled", () => {
        const css = rules("../components/ChapterNavigationButtons.vue");
        declaration(css, ".pureMonochrome .nav-btn:disabled", "color: #808080");
        declaration(css, ".pureMonochrome .nav-btn:disabled", "opacity: 1");
    });
    it("uses ink read markers and disabled grey tabs", () => {
        declaration(rules("../components/documents/BibleDocument.vue"), ".pureMonochrome .mark-as-read-icon", "border-color: black");
        declaration(rules("../components/tabs/TabNavigation.vue"), ".pureMonochrome .tab-button:disabled", "color: #808080");
    });
});
