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

// Resolve matching compiled selectors in specificity/source order (jsdom ignores specificity).
function cascaded(css, element, property) {
    const matches = [];
    for (const [, selectors, body] of css.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        for (const selector of selectors.split(",").map(s => s.trim())) {
            if (selector.includes("@") || /^(from|to|[\d.]+%)$/.test(selector) || !element.matches(selector)) continue;
            const specificity = (selector.match(/[.:#][\w-]+/g) || []).length;
            for (const [, name, value] of body.matchAll(/([\w-]+)\s*:\s*([^;]+);/g)) {
                if (name === property) matches.push({specificity, value: value.trim()});
            }
        }
    }
    return matches.sort((a, b) => a.specificity - b.specificity).at(-1)?.value;
}
function stateElement(rootClasses, classes) {
    const root = document.createElement("div");
    root.className = rootClasses;
    const element = document.createElement(classes.includes("button") ? "button" : "div");
    element.className = classes;
    root.appendChild(element);
    return element;
}

describe("pure monochrome compiled styles", () => {
    it("memorized menu uses ink in the winning cascade, leaving BW and normal unchanged", () => {
        const css = rules("../components/documents/MemorizeDocument.vue");
        expect(cascaded(css, stateElement("monochrome pureMonochrome", "menu-item memorized"), "color")).toBe("black");
        expect(cascaded(css, stateElement("monochrome pureMonochrome night", "menu-item memorized"), "color")).toBe("white");
        expect(cascaded(css, stateElement("monochrome", "menu-item memorized"), "color")).toBe("#4CAF50");
        expect(cascaded(css, stateElement("", "menu-item memorized"), "color")).toBe("#4CAF50");
    });
    it("pure settings popup cannot animate even with animations enabled", () => {
        const css = rules("../components/memorize/WordType.vue");
        for (const theme of ["monochrome pureMonochrome", "monochrome pureMonochrome night"]) {
            expect(cascaded(css, stateElement(theme, "settings-popup"), "animation")).toBe("none");
        }
        expect(cascaded(css, stateElement("", "settings-popup"), "animation")).toBe("settings-fade 0.15s ease");
    });
    it("pure completed text cannot animate even with animations enabled", () => {
        const css = rules("../components/memorize/WordType.vue");
        for (const theme of ["monochrome pureMonochrome", "monochrome pureMonochrome night"]) {
            expect(cascaded(css, stateElement(theme, "type-text completed"), "animation")).toBe("none");
            expect(cascaded(css, stateElement(theme, "settings-popup"), "animation")).toBe("none");
        }
        expect(cascaded(css, stateElement("monochrome", "type-text completed"), "animation")).toBe("completionPulse 2s");
        expect(cascaded(css, stateElement("", "settings-popup"), "animation")).toBe("settings-fade 0.15s ease");
    });
    for (const dark of [false, true]) for (const noAnimation of [false, true]) {
        const theme = `monochrome pureMonochrome${dark ? " night" : ""}${noAnimation ? " noAnimation" : ""}`;
        const ink = dark ? "white" : "black";
        const paper = dark ? "black" : "white";
        it(`actual BookmarkModal title overlay has no gradient dark=${dark} noAnimation=${noAnimation}`, () => {
            const css = rules("../components/modals/BookmarkModal.vue");
            const overlay = stateElement(theme, "overlay");
            const title = document.createElement("div");
            title.className = "bookmark-title";
            overlay.parentElement.appendChild(title);
            title.appendChild(overlay);
            expect(cascaded(css, overlay, "background")).toBe(paper);
        });
        it(`ordinary modal winning paints dark=${dark} noAnimation=${noAnimation}`, () => {
            const css = rules("../components/modals/ModalDialog.vue");
            for (const cls of ["modal-content", "modal-header", "modal-body", "modal-footer"]) {
                const el = stateElement(theme, cls);
                expect(cascaded(css, el, "background-color")).toBe(paper);
                expect(cascaded(css, el, "color")).toBe(ink);
            }
            const modal = stateElement(theme, "modal-content");
            expect(cascaded(css, modal, "border-color")).toBe(ink);
            expect(cascaded(css, modal, "box-shadow")).toBe("none");
            expect(cascaded(css, modal, "animation")).toBe("none");
        });
        it(`bookmark icon and experimental action winning paints dark=${dark} noAnimation=${noAnimation}`, () => {
            const css = rules("../components/modals/AskBookmarkSettings.vue");
            for (const cls of ["icon-item", "mode-toggle", "mode-select", "content-textarea", "validation-error", "cancel-button"]) {
                const el = stateElement(theme, cls);
                expect(cascaded(css, el, "background")).toBe(paper);
                expect(cascaded(css, el, "color")).toBe(ink);
                expect(cascaded(css, el, "border-color")).toBe(ink);
            }
            for (const cls of ["icon-item selected", "mode-toggle active", "save-button"]) {
                const el = stateElement(theme, cls);
                expect(cascaded(css, el, "background")).toBe(ink);
                expect(cascaded(css, el, "color")).toBe(paper);
            }
            const disabled = stateElement(theme, "save-button");
            disabled.setAttribute("disabled", "");
            expect(cascaded(css, disabled, "color")).toBe("#808080");
            expect(cascaded(css, disabled, "opacity")).toBe("1");
            expect(cascaded(css, stateElement(theme, "content-textarea has-error"), "box-shadow")).toBe("none");
            const notice = stateElement(theme, "experimental-notice");
            const common = rules("../common.scss");
            expect(cascaded(common, notice, "background")).toBe(paper);
            expect(cascaded(common, notice, "border-color")).toBe(ink);
        });
        it(`reading overlays are opaque independently of animation dark=${dark} noAnimation=${noAnimation}`, () => {
            for (const [path, cls] of [["../components/BibleView.vue", "pagenumber"], ["../components/ReadingProgress.vue", "reading-progress"]]) {
                const css = rules(path), el = stateElement(theme, cls);
                expect(cascaded(css, el, "background")).toBe(paper);
                expect(cascaded(css, el, "border-color")).toBe(ink);
            }
            const css = rules("../components/BibleView.vue");
            for (const cls of ["helper-line-thin-dotted", "helper-line-thin-solid", "helper-line-thick-solid"]) {
                const el = stateElement(theme, `scroll-helper-line ${cls}`);
                expect(cascaded(css, el, "opacity")).toBe("1");
                expect(cascaded(css, el, "border-top-color")).toBe(ink);
            }
        });
    }
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
