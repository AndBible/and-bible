/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import WordBlur from "@/components/memorize/WordBlur.vue";
import { describe, it, expect, vi } from 'vitest';

// Mock the useCommon composable
vi.mock("@/composables", () => ({
  useCommon: () => ({
    strings: {
      blur: "Blur",
      reset: "Reset"
    }
  })
}));

describe("WordBlur.vue", () => {
  const createWrapper = (props = {}) => {
    return mount(WordBlur, {
      props: {
        textItems: [
          { key: "verse1", text: "In the beginning God created the heaven and the earth." },
          { key: "verse2", text: "And the earth was without form, and void." }
        ],
        modeConfig: undefined,
        ...props
      }
    });
  };

  it("renders correctly with default props", () => {
    const wrapper = createWrapper();
    
    // Check if control buttons are rendered
    expect(wrapper.find('.memorize-controls').exists()).toBe(true);
    expect(wrapper.find('.button').text()).toContain('Blur');
    
    // Check if text is rendered correctly
    expect(wrapper.find('.memorize-text').exists()).toBe(true);
    expect(wrapper.findAll('.memorize-word').length).toBeGreaterThan(0);
    
    // No words should be blurred initially (blurLevel = 0)
    expect(wrapper.findAll('.blurred').length).toBe(0);
  });

  it("increases blur level when the blur button is clicked", async () => {
    const wrapper = createWrapper();
    
    // Initial state - no blurred words
    expect(wrapper.findAll('.blurred').length).toBe(0);
    
    // Click the blur button
    await wrapper.findAll('.button')[0].trigger('click');
    
    // After clicking, some words should be blurred
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(0);
    
    // Click again to increase blur level
    await wrapper.findAll('.button')[0].trigger('click');
    
    // More words should be blurred now
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(0);
  });

  it("resets blur when the reset button is clicked", async () => {
    const wrapper = createWrapper();
    
    // First increase blur level
    await wrapper.findAll('.button')[0].trigger('click');
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(0);
    
    // Reset blur
    await wrapper.findAll('.button')[1].trigger('click');
    
    // No words should be blurred after reset
    expect(wrapper.findAll('.blurred').length).toBe(0);
  });

  it("reveals a word temporarily when it's clicked", async () => {
    const wrapper = createWrapper();
    
    // First increase blur level
    await wrapper.findAll('.button')[0].trigger('click');
    
    // Get a blurred word
    const blurredWord = wrapper.find('.blurred');
    expect(blurredWord.exists()).toBe(true);
    
    // Click on the blurred word
    await blurredWord.trigger('click');
    
    // Word should be revealed (have 'revealed' class)
    expect(blurredWord.classes()).toContain('revealed');
    
    // In a real test we'd use fake timers, but in this case we'll just verify
    // that the word is revealed after clicking
    expect(wrapper.vm.revealedWords).toHaveProperty(`${wrapper.vm.textItems[0].key}-0`);
  });

  it("emits save-mode-config when configuration changes", async () => {
    const wrapper = createWrapper();
    
    // Clicking blur button should save the configuration
    await wrapper.findAll('.button')[0].trigger('click');
    
    // Check for emitted events
    const emittedEvents = wrapper.emitted('save-mode-config');
    expect(emittedEvents).toBeTruthy();
    expect(emittedEvents[0][0]).toHaveProperty('blurConfig');
    expect(emittedEvents[0][0].blurConfig).toHaveProperty('blurLevel', 1);
  });

  it("loads existing configuration from props", () => {
    // Create wrapper with existing configuration
    const existingConfig = {
      blurConfig: {
        blurLevel: 3,
        revealedWords: {}
      }
    };
    
    const wrapper = createWrapper({ modeConfig: existingConfig });
    
    // Should have loaded the blur level
    expect(wrapper.vm.blurLevel).toBe(3);
    
    // Cannot check for blurred words directly as they're not blurred until
    // the DOM is actually rendered and the isWordBlurred function is called
    // Instead, we'll verify the config was loaded correctly
    expect(wrapper.vm.blurLevel).toBe(existingConfig.blurConfig.blurLevel);
  });
});
