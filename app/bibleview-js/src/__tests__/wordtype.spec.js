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
import { nextTick } from 'vue';

vi.mock("@/composables", () => ({
    useCommon: () => ({
        strings: {
            typeEverything: "Type full words",
            reset: "Reset",
            tapToStartTyping: "Tap here to start typing"
        }
    })
}));

describe("WordType.vue", () => {
    const textItems = [
        { key: "verse1", text: "For God so loved" },
        { key: "verse2", text: "the world." }
    ];

    const createWrapper = (props = {}) => {
        return mount(WordType, {
            props: {
                textItems,
                modeConfig: undefined,
                ...props
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
        const buttons = wrapper.findAll(".memorize-controls .button");
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

        // Enable type everything
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
});
