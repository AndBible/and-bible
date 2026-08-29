/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import { mount } from "@vue/test-utils";
import WordType from "@/components/memorize/WordType.vue";
import { describe, it, expect, vi } from 'vitest';
import { nextTick, reactive } from 'vue';
import { readingProgressSettingsKey } from "@/types/constants";

vi.mock("@/composables", () => ({
    useCommon: () => ({
        strings: {
            typeEverything: "Type full words",
            reset: "Reset",
            tapToStartTyping: "Tap here to start typing",
            wordVisibility: "Word visibility",
            wordVisibilityLight: "Light",
            wordVisibilityDim: "Dim",
            wordVisibilityHidden: "Hidden",
            errorHeatmap: "Error heatmap"
        }
    })
}));

describe("WordType.vue", () => {
    const textItems = [
        { key: "verse1", text: "For God so loved" },
        { key: "verse2", text: "the world." }
    ];

    const createProgressSettings = () => ({
        settings: reactive({
            autoMarkMemorized: true,
            memorizeTypeFullWords: false,
            memorizeWordVisibility: 'light',
            memorizeErrorHeatmap: true,
            memorizeScrambleHideUsed: false,
        }),
        updateSettings: vi.fn(),
    });

    const createWrapper = (props = {}) => {
        return mount(WordType, {
            props: {
                textItems,
                modeConfig: undefined,
                ...props
            },
            global: {
                provide: {
                    [readingProgressSettingsKey]: createProgressSettings(),
                }
            }
        });
    };

    it("renders all words with type-unreached or type-current class initially", () => {
        const wrapper = createWrapper();
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        // "For" should be current (first word), rest unreached
        expect(words[0].classes()).toContain("type-current");
        // Remaining words should be unreached
        for (let i = 1; i < words.length; i++) {
            if (!words[i].classes().includes("punctuation")) {
                expect(words[i].classes()).toContain("type-unreached");
            }
        }
    });

    it("marks word as correct when first letter is typed correctly", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Type 'F' for "For"
        await input.setValue('F');
        await input.trigger('input');

        // "For" should now be correct, "God" should be current
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].classes()).toContain("type-correct");
        expect(words[1].classes()).toContain("type-current");
    });

    it("shows incorrect state on wrong letter", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Type 'X' for "For" - wrong
        await input.setValue('X');
        await input.trigger('input');

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].classes()).toContain("type-incorrect");
    });

    it("skips punctuation tokens automatically", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Type through "For God so loved the world" (6 words)
        const firstLetters = ['F', 'G', 's', 'l', 't', 'w'];
        for (const letter of firstLetters) {
            await input.setValue(letter);
            await input.trigger('input');
        }

        // The period after "world" should be auto-skipped
        // All non-punctuation words should be correct
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        for (const word of words) {
            expect(word.classes()).toContain("type-correct");
        }
    });

    it("emits memorize-completed when all words are typed", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        const firstLetters = ['F', 'G', 's', 'l', 't', 'w'];
        for (const letter of firstLetters) {
            await input.setValue(letter);
            await input.trigger('input');
        }

        expect(wrapper.emitted('memorize-completed')).toBeTruthy();
    });

    it("emits save-mode-config on word advance", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        await input.setValue('F');
        await input.trigger('input');

        expect(wrapper.emitted('save-mode-config')).toBeTruthy();
        const lastConfig = wrapper.emitted('save-mode-config').at(-1)[0];
        expect(lastConfig.typeConfig.currentWordIndex).toBeGreaterThan(0);
    });

    it("resets state when reset button is clicked", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Advance a few words
        await input.setValue('F');
        await input.trigger('input');
        await input.setValue('G');
        await input.trigger('input');

        // Click reset
        const buttons = wrapper.findAll(".memorize-controls .icon-button");
        await buttons[0].trigger("click");

        // First word should be current again
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].classes()).toContain("type-current");
    });

    it("restores state from modeConfig", async () => {
        const wrapper = createWrapper({
            modeConfig: {
                typeConfig: {
                    currentWordIndex: 2, // Skip "For" and "God"
                    typeEverything: false
                }
            }
        });
        await nextTick();

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        // "For" and "God" should be correct
        expect(words[0].classes()).toContain("type-correct");
        expect(words[1].classes()).toContain("type-correct");
        // "so" should be current
        expect(words[2].classes()).toContain("type-current");
    });

    it("supports type everything mode", async () => {
        const wrapper = createWrapper();

        // Open settings popup and enable type everything
        await wrapper.find('.settings-trigger').trigger('click');
        const checkbox = wrapper.find('input[type="checkbox"]');
        await checkbox.setValue(true);

        const input = wrapper.find(".type-hidden-input");

        // Type "For" character by character - should auto-advance on last char
        await input.setValue('F');
        await input.trigger('input');
        await input.setValue('o');
        await input.trigger('input');
        await input.setValue('r');
        await input.trigger('input');

        // "For" should be correct now
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].classes()).toContain("type-correct");
    });

    it("shows tap hint when input is not focused", () => {
        const wrapper = createWrapper();
        expect(wrapper.find(".tap-hint").exists()).toBe(true);
    });

    it("opens settings popup when gear icon is clicked", async () => {
        const wrapper = createWrapper();
        expect(wrapper.find(".settings-popup").exists()).toBe(false);
        await wrapper.find(".settings-trigger").trigger("click");
        expect(wrapper.find(".settings-popup").exists()).toBe(true);
    });

    it("applies visibility-light class by default", () => {
        const wrapper = createWrapper();
        const unreachedWords = wrapper.findAll(".type-unreached");
        expect(unreachedWords.length).toBeGreaterThan(0);
        for (const word of unreachedWords) {
            expect(word.classes()).toContain("visibility-light");
        }
    });

    it("applies visibility class from restored config", async () => {
        const wrapper = createWrapper({
            modeConfig: {
                typeConfig: {
                    currentWordIndex: 0,
                    typeEverything: false,
                    wordVisibility: 'hidden'
                }
            }
        });
        await nextTick();
        const unreachedWords = wrapper.findAll(".type-unreached");
        expect(unreachedWords.length).toBeGreaterThan(0);
        for (const word of unreachedWords) {
            expect(word.classes()).toContain("visibility-hidden");
        }
    });

    it("applies heatmap class on incorrect input", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Type wrong letter twice for "For"
        await input.setValue('X');
        await input.trigger('input');
        await input.setValue('Z');
        await input.trigger('input');

        // The current word should have heatmap-2 class after two errors
        const currentWord = wrapper.find(".type-current");
        expect(currentWord.classes()).toContain("heatmap-2");
    });

    it("caps heatmap level at 3 even after many errors", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Type wrong letter five times for "For"
        for (let i = 0; i < 5; i++) {
            await input.setValue('X');
            await input.trigger('input');
        }

        const currentWord = wrapper.find(".type-current");
        const classes = currentWord.classes();
        expect(classes).toContain("heatmap-3");
        expect(classes).not.toContain("heatmap-4");
        expect(classes).not.toContain("heatmap-5");

        // saved errorCount should also be capped at 3
        const configs = wrapper.emitted('save-mode-config');
        const lastConfig = configs.at(-1)[0];
        expect(lastConfig.typeConfig.errorCounts[0]).toBe(3);
    });

    it("does not apply heatmap when disabled", async () => {
        const wrapper = createWrapper({
            modeConfig: {
                typeConfig: {
                    currentWordIndex: 0,
                    typeEverything: false,
                    wordVisibility: 'light',
                    errorHeatmap: false,
                    errorCounts: {0: 3},
                }
            }
        });
        await nextTick();

        const currentWord = wrapper.find(".type-current");
        const classes = currentWord.classes();
        expect(classes.some(c => c.startsWith("heatmap-"))).toBe(false);
    });

    it("preserves error counts on reset", async () => {
        const wrapper = createWrapper();
        const input = wrapper.find(".type-hidden-input");

        // Make two errors to build heatmap
        await input.setValue('X');
        await input.trigger('input');
        await input.setValue('Z');
        await input.trigger('input');

        // Click reset
        const buttons = wrapper.findAll(".memorize-controls .icon-button");
        await buttons[0].trigger("click");
        await nextTick();

        // Current word should still have heatmap-2 class (preserved across reset)
        const currentWord = wrapper.find(".type-current");
        expect(currentWord.classes()).toContain("heatmap-2");

        // saved errorCounts should still contain the entry
        const configs = wrapper.emitted('save-mode-config');
        const lastConfig = configs.at(-1)[0];
        expect(lastConfig.typeConfig.errorCounts[0]).toBe(2);
    });

    it("decrements heatmap level when word is typed correctly", async () => {
        // Start with errorCounts already populated
        const wrapper = createWrapper({
            modeConfig: {
                typeConfig: {
                    currentWordIndex: 0,
                    typeEverything: false,
                    wordVisibility: 'light',
                    errorHeatmap: true,
                    errorCounts: {0: 3, 1: 1},
                }
            }
        });
        await nextTick();

        const input = wrapper.find(".type-hidden-input");

        // Type "For" correctly — should decrement errorCounts[0] from 3 to 2
        await input.setValue('F');
        await input.trigger('input');

        const configs = wrapper.emitted('save-mode-config');
        const config1 = configs.at(-1)[0];
        expect(config1.typeConfig.errorCounts[0]).toBe(2);

        // Type "God" correctly — should remove errorCounts[1] (was 1, becomes 0)
        await input.setValue('G');
        await input.trigger('input');

        const config2 = wrapper.emitted('save-mode-config').at(-1)[0];
        expect(config2.typeConfig.errorCounts[1]).toBeUndefined();
    });

    it("shows text and heatmap before typing starts when error history exists", async () => {
        // Restore state with existing errorCounts but currentWordIndex=0 (pre-typing)
        const wrapper = createWrapper({
            modeConfig: {
                typeConfig: {
                    currentWordIndex: 0,
                    typeEverything: false,
                    wordVisibility: 'light',
                    errorHeatmap: true,
                    errorCounts: {2: 2},
                }
            }
        });
        await nextTick();

        // Text block should be present (no longer hidden via .hidden class)
        const textBlock = wrapper.find(".text-block");
        expect(textBlock.classes()).not.toContain("hidden");

        // Tap hint should still be shown initially
        expect(wrapper.find(".tap-hint").exists()).toBe(true);

        // Word with index 2 should have heatmap-2 class
        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[2].classes()).toContain("heatmap-2");
    });

    it("treats a word with a straight apostrophe as a single token in type-everything mode", async () => {
        const wrapper = createWrapper({
            textItems: [{ key: "verse1", text: "Lord's prayer" }],
        });

        // Enable type everything
        await wrapper.find('.settings-trigger').trigger('click');
        const checkbox = wrapper.find('input[type="checkbox"]');
        await checkbox.setValue(true);

        const input = wrapper.find(".type-hidden-input");
        for (const ch of "Lord's") {
            await input.setValue(ch);
            await input.trigger('input');
        }

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].text()).toBe("Lord's");
        expect(words[0].classes()).toContain("type-correct");
        expect(words[0].classes()).not.toContain("type-incorrect");
        expect(words[1].classes()).toContain("type-current");
    });

    it("accepts a straight apostrophe typed against a curly apostrophe in the source text", async () => {
        const wrapper = createWrapper({
            textItems: [{ key: "verse1", text: "Lord’s prayer" }],
        });

        await wrapper.find('.settings-trigger').trigger('click');
        const checkbox = wrapper.find('input[type="checkbox"]');
        await checkbox.setValue(true);

        const input = wrapper.find(".type-hidden-input");
        for (const ch of "Lord's") {
            await input.setValue(ch);
            await input.trigger('input');
        }

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].classes()).toContain("type-correct");
        expect(words[0].classes()).not.toContain("type-incorrect");
        expect(words[1].classes()).toContain("type-current");
    });

    it("merges a trailing apostrophe into a word ending in s (plural/classical possessive)", async () => {
        const wrapper = createWrapper({
            textItems: [{ key: "verse1", text: "Jesus' disciples" }],
        });

        await wrapper.find('.settings-trigger').trigger('click');
        const checkbox = wrapper.find('input[type="checkbox"]');
        await checkbox.setValue(true);

        const input = wrapper.find(".type-hidden-input");
        for (const ch of "Jesus'") {
            await input.setValue(ch);
            await input.trigger('input');
        }

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        expect(words[0].text()).toBe("Jesus'");
        expect(words[0].classes()).toContain("type-correct");
        expect(words[0].classes()).not.toContain("type-incorrect");
        expect(words[1].classes()).toContain("type-current");
    });

    it("does not merge a trailing apostrophe into a word not ending in s (closing quote)", async () => {
        const wrapper = createWrapper({
            textItems: [{ key: "verse1", text: "he said 'Peace' loudly" }],
        });
        await nextTick();

        const words = wrapper.findAll(".type-word:not(.punctuation)");
        // "Peace" stays its own token; the closing quote is a separate punctuation token
        expect(words.map(w => w.text())).toEqual(["he", "said", "Peace", "loudly"]);
    });

    it("saves wordVisibility in config", async () => {
        const wrapper = createWrapper();
        // Open settings popup
        await wrapper.find(".settings-trigger").trigger("click");
        // Select 'dim' radio
        const radios = wrapper.findAll('input[type="radio"]');
        await radios[1].setValue(); // 'dim' is the second option
        const configs = wrapper.emitted('save-mode-config');
        const lastConfig = configs.at(-1)[0];
        expect(lastConfig.typeConfig.wordVisibility).toBe('dim');
    });
});
