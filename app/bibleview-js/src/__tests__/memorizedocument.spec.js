/*
 * Copyright (c) 2021-2022 Martin Denh// Mock the useCommon composable
vi.mock("@/composables", () => ({
  useCommon: () => ({
    strings: {
      wordBlur: "Word Blur",
      wordScramble: "Word Scramble"
    },
    android: {
      saveState: vi.fn()
    }
  })
}));iraksinen and the AndBible contributors.
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
import MemorizeDocument from "@/components/documents/MemorizeDocument.vue";
import WordBlur from "@/components/memorize/WordBlur.vue";
import WordScramble from "@/components/memorize/WordScramble.vue";
import { describe, it, expect, vi } from 'vitest';
import { MemorizeStateModeEnum } from "@/types/documents";

// Mock composables
vi.mock("@/composables", () => ({
  useCommon: () => ({
    strings: {
      wordBlur: "Word Blur",
      wordScramble: "Word Scramble"
    },
    android: {
      saveState: vi.fn()
    }
  })
}));

describe("MemorizeDocument.vue", () => {
  const createMockDocument = (overrides = {}) => ({
    id: "doc1",
    type: "memorize",
    title: "Memory Verse - John 3:16",
    texts: [
      { key: "verse1", text: "For God so loved the world, that he gave his only Son," },
      { key: "verse2", text: "that whoever believes in him should not perish but have eternal life." }
    ],
    state: {
      memorize: {
        mode: MemorizeStateModeEnum.BLUR,
        modeConfig: {}
      }
    },
    ...overrides
  });

  const createWrapper = (docOverrides = {}) => {
    return mount(MemorizeDocument, {
      props: {
        document: createMockDocument(docOverrides)
      },
      global: {
        stubs: {
          WordBlur: true,
          WordScramble: true
        }
      }
    });
  };

  it("renders the document title correctly", () => {
    const wrapper = createWrapper();
    expect(wrapper.find("h2").text()).toBe("Memory Verse - John 3:16");
  });

  it("renders the mode selector buttons", () => {
    const wrapper = createWrapper();
    const buttons = wrapper.findAll(".memorize-mode-selector .tab-button");
    
    expect(buttons.length).toBe(2);
    expect(buttons[0].text()).toBe("Word Blur");
    expect(buttons[1].text()).toBe("Word Scramble");
  });

  it("shows the blur mode component by default", () => {
    const wrapper = createWrapper();
    expect(wrapper.findComponent(WordBlur).exists()).toBe(true);
    // With TabContainer, inactive components might still exist in DOM but be hidden
    // Check for the correct active panel instead
    const blurPanel = wrapper.find('[id="tabpanel-blur"]');
    const scramblePanel = wrapper.find('[id="tabpanel-scramble"]');
    expect(blurPanel.isVisible()).toBe(true);
    if (scramblePanel.exists()) {
      expect(scramblePanel.isVisible()).toBe(false);
    }
  });

  it("switches to scramble mode when button is clicked", async () => {
    const wrapper = createWrapper();
    
    // Initially in blur mode
    expect(wrapper.findComponent(WordBlur).exists()).toBe(true);
    
    // Click on the scramble mode button
    const buttons = wrapper.findAll(".memorize-mode-selector .tab-button");
    if (buttons.length > 1) {
      await buttons[1].trigger("click");
      
      // Should switch to scramble mode
      expect(wrapper.findComponent(WordBlur).exists()).toBe(true);
      expect(wrapper.findComponent(WordScramble).exists()).toBe(true);
      
      // Check panel visibility
      const blurPanel = wrapper.find('[id="tabpanel-blur"]');
      const scramblePanel = wrapper.find('[id="tabpanel-scramble"]');
      if (blurPanel.exists()) expect(blurPanel.isVisible()).toBe(false);
      expect(scramblePanel.isVisible()).toBe(true);
    }
  });

  it("provides the correct props to the child component", () => {
    const wrapper = createWrapper();
    const childComponent = wrapper.findComponent(WordBlur);
    
    // Should pass the text items
    expect(childComponent.props('textItems')).toEqual([
      { key: "verse1", text: "For God so loved the world, that he gave his only Son," },
      { key: "verse2", text: "that whoever believes in him should not perish but have eternal life." }
    ]);
    
    // Should pass the mode config
    expect(childComponent.props('modeConfig')).toEqual({});
  });

  it("saves state when mode is changed", async () => {
    const wrapper = createWrapper();
    
    // Get the mock save function
    const mockSaveState = wrapper.vm.android.saveState;
    
    // Change mode
    const buttons = wrapper.findAll(".memorize-mode-selector .tab-button");
    if (buttons.length > 1) {
      await buttons[1].trigger("click");
      
      // Should save state
      expect(mockSaveState).toHaveBeenCalled();
    }
  });

  it("handles the save-mode-config event from child components", async () => {
    const wrapper = createWrapper();
    const childComponent = wrapper.findComponent(WordBlur);
    
    // Emit save-mode-config from child
    const newConfig = { blurConfig: { blurLevel: 2, revealedWords: {} } };
    await childComponent.vm.$emit('save-mode-config', newConfig);
    
    // Parent should update its modeConfig
    // We can't directly check the refs, but we can verify it handles the event
    // by checking if the saveState function was called
    const mockAndroid = wrapper.vm.android;
    expect(mockAndroid.saveState).toHaveBeenCalled();
  });
  
  it("restores previous mode from document state", () => {
    // Create document with scramble mode selected
    const wrapper = createWrapper({
      state: {
        memorize: {
          mode: MemorizeStateModeEnum.SCRAMBLE,
          modeConfig: {}
        }
      }
    });
    
    // Should start in scramble mode
    expect(wrapper.findComponent(WordBlur).exists()).toBe(true);
    expect(wrapper.findComponent(WordScramble).exists()).toBe(true);
    
    // Check panel visibility for scramble mode
    const blurPanel = wrapper.find('[id="tabpanel-blur"]');
    const scramblePanel = wrapper.find('[id="tabpanel-scramble"]');
    if (blurPanel.exists()) expect(blurPanel.isVisible()).toBe(false);
    expect(scramblePanel.isVisible()).toBe(true);
    
    // The scramble button should be active (not toggled since we're using TabContainer)
    const buttons = wrapper.findAll(".memorize-mode-selector .tab-button");
    if (buttons.length > 1) {
      expect(buttons[1].classes()).toContain("active");
    }
  });
});
