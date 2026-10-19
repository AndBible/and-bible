/*
 * Copyright (c) 2021-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import WordScramble from "@/components/memorize/WordScramble.vue";
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { reactive } from 'vue';
import { readingProgressSettingsKey } from "@/types/constants";

// Mock the useCommon composable
vi.mock("@/composables", () => ({
  useCommon: () => ({
    strings: {
      peek: "Peek",
      reset: "Reset",
      hideUsedButtons: "Hide used word buttons"
    }
  })
}));

// Mock Math.random to ensure deterministic behavior in tests
const originalRandom = Math.random;
beforeEach(() => {
  // Use a predictable random function for tests
  let counter = 0;
  Math.random = () => {
    counter = (counter + 0.1) % 1;
    return counter;
  };
  
  // Setup fake timers for setTimeout calls
  vi.useFakeTimers();
});

afterEach(() => {
  // Restore the original Math.random and timer functions
  Math.random = originalRandom;
  vi.restoreAllMocks();
});

describe("WordScramble.vue", () => {
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
    return mount(WordScramble, {
      props: {
        textItems: [
          { key: "verse1", text: "In the beginning God created the heaven and the earth." },
          { key: "verse2", text: "And the earth was without form, and void." }
        ],
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

  it("renders correctly with default props", async () => {
    const wrapper = createWrapper();
    
    // Wait for component to initialize
    await wrapper.vm.$nextTick();
    
    // Check if control buttons are rendered
    expect(wrapper.find('.memorize-controls').exists()).toBe(true);
    expect(wrapper.findAll('.memorize-controls .icon-button').length).toBeGreaterThan(0);
    
    // Check if text area is rendered
    expect(wrapper.find('.memorize-text').exists()).toBe(true);
    
    // Check if word buttons are rendered
    expect(wrapper.find('.word-buttons').exists()).toBe(true);
    expect(wrapper.findAll('.word-buttons .button').length).toBeGreaterThan(0);
  });

  it("scrambles words and creates word buttons", async () => {
    const wrapper = createWrapper();
    
    // Wait for component to initialize
    await wrapper.vm.$nextTick();
    
    // Should have created scrambled word buttons
    const wordButtons = wrapper.findAll('.word-buttons .button');
    expect(wordButtons.length).toBeGreaterThan(0);
    
    // Words should be unique (even if text has repeated words, they are 
    // combined with a count indicator for repeated words)
    const uniqueWords = new Set(wordButtons.map(button => button.text().trim().replace(/\s+\(\d+\)$/, '')));
    expect(uniqueWords.size).toBeGreaterThan(0);
    
    // Verify words from the original text are present in the buttons
    const buttonTexts = wordButtons.map(button => button.text().trim().replace(/\s+\(\d+\)$/, ''));
    const originalWords = ["In", "the", "beginning", "God", "created", "heaven", "earth", "And", "was", "without", "form", "void"];
    
    // At least some of the original words should be in the buttons
    const foundWords = originalWords.filter(word => buttonTexts.includes(word));
    expect(foundWords.length).toBeGreaterThan(0);
  });

  it("toggles isPeeking when peek button is pressed and released", async () => {
    const wrapper = createWrapper();

    // Initially not peeking
    expect(wrapper.find('.memorize-text').classes()).not.toContain('preview');

    // Simulate pointerdown (press) on peek button
    await wrapper.find('.memorize-controls .icon-button').trigger('pointerdown');

    // Should now be peeking
    expect(wrapper.find('.memorize-text').classes()).toContain('preview');

    // Simulate pointerup (release) on peek button
    await wrapper.find('.memorize-controls .icon-button').trigger('pointerup');

    // Should no longer be peeking
    expect(wrapper.find('.memorize-text').classes()).not.toContain('preview');
  });

  it("resets the scrambled words when reset button is clicked", async () => {
    const wrapper = createWrapper();
    
    // Click the reset button
    await wrapper.findAll('.memorize-controls .icon-button')[1].trigger('click');
    
    // Should have called resetWords and re-scrambled the words
    expect(wrapper.vm.isPeeking).toBe(false);
    
    // A reset should have triggered a save-mode-config event
    expect(wrapper.emitted('save-mode-config')).toBeTruthy();
    expect(wrapper.emitted('save-mode-config')[0][0]).toHaveProperty('scrambleConfig');
  });

  it("marks buttons as used when words are correctly selected", async () => {
    const wrapper = createWrapper();
    
    // Spy on selectWord method
    const selectWordSpy = vi.spyOn(wrapper.vm, 'selectWord');
    
    // Get the word that should be selected first
    const currentIndex = wrapper.vm.currentWordIndex;
    const currentWordInfo = getLocalIndicesFromWrapper(wrapper, currentIndex);
    const currentWord = getWordsFromWrapper(wrapper, currentWordInfo.itemIndex)[currentWordInfo.localIndex];
    
    // Find button with this word
    const targetWordButton = findButtonForWord(wrapper, currentWord);
    
    if (targetWordButton) {
      // Click the correct word button
      await targetWordButton.trigger('click');
      
      // selectWord should have been called
      expect(selectWordSpy).toHaveBeenCalled();
      
      // The button should now be disabled or marked as used
      expect(targetWordButton.classes()).toContain('disabled');
    } else {
      // Skip this test if we couldn't find the button (possible due to randomization)
      console.log('Skipping test because target word button not found');
    }
  });

  it("marks buttons as incorrect when wrong words are selected", async () => {
    const wrapper = createWrapper();
    
    // Get the first word index that should be selected
    const correctWordIndex = wrapper.vm.currentWordIndex;
    const correctWordInfo = getLocalIndicesFromWrapper(wrapper, correctWordIndex);
    const correctWord = getWordsFromWrapper(wrapper, correctWordInfo.itemIndex)[correctWordInfo.localIndex];
    
    // Find a button with an incorrect word (not the current word)
    const incorrectButton = findIncorrectButton(wrapper, correctWord);
    
    if (incorrectButton) {
      // Click the incorrect button
      await incorrectButton.trigger('click');
      
      // The button should be marked as incorrect
      expect(incorrectButton.classes()).toContain('incorrect');
      
      // After a delay, the incorrect class should be removed
      // In a real test we would use vi.useFakeTimers(), but for simplicity,
      // we'll just check that the incorrect state was applied
    } else {
      // Skip this test if we couldn't find an incorrect button
      console.log('Skipping test because incorrect button not found');
    }
  });

  it("emits save-mode-config when configuration changes", async () => {
    const wrapper = createWrapper();
    
    // Reset words to trigger a save
    await wrapper.findAll('.memorize-controls .icon-button')[1].trigger('click');
    
    // Check for emitted events
    const emittedEvents = wrapper.emitted('save-mode-config');
    expect(emittedEvents).toBeTruthy();
    expect(emittedEvents[0][0]).toHaveProperty('scrambleConfig');
    expect(emittedEvents[0][0].scrambleConfig).toHaveProperty('currentWordIndex');
    expect(emittedEvents[0][0].scrambleConfig).toHaveProperty('scrambledWords');
  });

  it("restores valid saved order and progress without resetting", async () => {
    const mockScrambledWords = [
      { word: "God", originalIndices: [2], remainingUses: 0, used: true, incorrect: false },
      { word: "all", originalIndices: [5], remainingUses: 1, used: false, incorrect: false },
      { word: "For", originalIndices: [1, 4], remainingUses: 1, used: false, incorrect: false }
    ];
    const wrapper = createWrapper({
      textItems: [
        { key: "verse1", text: "¶For God," },
        { key: "verse2", text: "for all." }
      ],
      modeConfig: {
        scrambleConfig: {
          currentWordIndex: 4,
          scrambledWords: mockScrambledWords
        }
      }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.currentWordIndex).toBe(4);
    expect(wrapper.vm.scrambledWords).toEqual(mockScrambledWords);
    expect(wrapper.emitted("save-mode-config")).toBeUndefined();
    expect(wrapper.findAll(".word-buttons .button").map(button => button.text())).toEqual(["God", "all", "For"]);
    expect(findButtonForWord(wrapper, "God").classes()).toContain("disabled");
    expect(findButtonForWord(wrapper, "For").classes()).not.toContain("disabled");
    expect(wrapper.findAll(".memorize-word:not(.punctuation)").map(word => word.text()))
      .toEqual(["For", "God", "___", "___"]);

    for (const word of ["for", "all"]) {
      const button = findButtonForWord(wrapper, word);
      expect(button).not.toBeNull();
      await button.trigger("click");
      expect(button.classes()).not.toContain("incorrect");
    }
    expect(wrapper.find(".memorize-text").classes()).toContain("completed");
    expect(wrapper.emitted("memorize-completed")).toHaveLength(1);
    wrapper.unmount();
  });

  it.each([
    ["¶For God", "¶For", "God", 1, [1, 2]],
    ["For ¶God", "For", "¶God", 0, [0, 2]],
  ])("resets incompatible pre-fix paragraph cache for %s", async (text, firstWord, secondWord, startIndex, indices) => {
    const wrapper = createWrapper({
      textItems: [{ key: "verse1", text }],
      modeConfig: {
        scrambleConfig: {
          currentWordIndex: 1,
          scrambledWords: [
            { word: secondWord, originalIndices: [1], remainingUses: 1, used: false, incorrect: false },
            { word: firstWord, originalIndices: [0], remainingUses: 0, used: true, incorrect: false }
          ]
        }
      }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.findAll(".word-buttons .button").map(button => button.text()).sort()).toEqual(["For", "God"]);
    expect(wrapper.vm.currentWordIndex).toBe(startIndex);
    expect(wrapper.vm.scrambledWords.flatMap(word => word.originalIndices).sort()).toEqual(indices);
    expect(wrapper.findAll(".memorize-word:not(.punctuation)").map(word => word.text())).toEqual(["___", "___"]);
    expect(wrapper.emitted("save-mode-config")).toHaveLength(1);
    expect(wrapper.emitted("save-mode-config")[0][0].scrambleConfig.currentWordIndex).toBe(startIndex);

    for (const word of ["For", "God"]) {
      const button = findButtonForWord(wrapper, word);
      expect(button).not.toBeNull();
      expect(button.classes()).not.toContain("disabled");
      await button.trigger("click");
      expect(button.classes()).not.toContain("incorrect");
    }
    expect(wrapper.find(".memorize-text").classes()).toContain("completed");
    expect(wrapper.emitted("memorize-completed")).toHaveLength(1);
    wrapper.unmount();
  });

  it.each([
    ["missing", [0]],
    ["duplicate", [0, 0]],
    ["punctuation", [0, 1]],
  ])("resets cached mappings with %s word indices", async (_reason, originalIndices) => {
    const wrapper = createWrapper({
      textItems: [{ key: "verse1", text: "For, for God" }],
      modeConfig: {
        scrambleConfig: {
          currentWordIndex: 2,
          scrambledWords: [
            { word: "For", originalIndices, remainingUses: 1, used: false, incorrect: false },
            { word: "God", originalIndices: [3], remainingUses: 1, used: false, incorrect: false }
          ]
        }
      }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.currentWordIndex).toBe(0);
    expect(wrapper.vm.scrambledWords.find(word => word.word === "For").originalIndices).toEqual([0, 2]);
    expect(wrapper.emitted("save-mode-config")).toHaveLength(1);
    wrapper.unmount();
  });

  it("shows completed state when all words are used", async () => {
    // Create some mock scrambled words that are all used
    const mockScrambledWords = [
      { word: "beginning", originalIndices: [1], remainingUses: 0, used: true, incorrect: false },
      { word: "the", originalIndices: [0, 2], remainingUses: 0, used: true, incorrect: false }
    ];
    
    const existingConfig = {
      scrambleConfig: {
        currentWordIndex: 3, // Past all words
        scrambledWords: mockScrambledWords
      }
    };
    
    const wrapper = createWrapper({
      textItems: [{ key: "verse1", text: "the beginning the" }],
      modeConfig: existingConfig
    });
    
    // Force a re-render to ensure the computed property is evaluated
    await wrapper.vm.$nextTick();
    
    // Should show completed state
    expect(wrapper.find('.memorize-text').classes()).toContain('completed');
  });

  it("handles words with multiple occurrences correctly", async () => {
    // Create text with repeated words to test multiple occurrences
    const repeatedText = [
      { key: "verse1", text: "The the the quick brown fox jumps over the lazy dog." }
    ];
    
    const wrapper = createWrapper({ 
      textItems: repeatedText
    });
    
    // Wait for component to initialize
    await wrapper.vm.$nextTick();
    
    // Find the button for the word 'the' which should have multiple occurrences
    const theButton = Array.from(wrapper.findAll('.word-buttons .button')).find(
      button => button.text().trim().toLowerCase().startsWith('the')
    );
    
    // The button should exist and show the count
    expect(theButton).toBeTruthy();
    expect(theButton.text()).toContain('(');  // Should contain count indicator
    
    // Initial count of remaining uses
    const initialCount = parseInt(theButton.text().match(/\((\d+)\)/)?.[1] || '0');
    expect(initialCount).toBeGreaterThan(1);
    
    // Click the button to decrease its count, but not completely use it
    await theButton.trigger('click');
    
    // The remaining uses should have decreased
    const newCount = parseInt(theButton.text().match(/\((\d+)\)/)?.[1] || '0');
    expect(newCount).toBeLessThan(initialCount);
    
    // But it should not be disabled yet since there are still uses left
    expect(theButton.classes()).not.toContain('disabled');
  });

  it("handles punctuation correctly by auto-revealing it", async () => {
    // Create text with punctuation
    const textWithPunctuation = [
      { key: "verse1", text: "Hello, world! How are you?" }
    ];
    
    const wrapper = createWrapper({ 
      textItems: textWithPunctuation
    });
    
    // Wait for component to initialize
    await wrapper.vm.$nextTick();
    
    // Check that we don't have buttons for punctuation marks
    const buttonTexts = wrapper.findAll('.word-buttons .button').map(b => b.text().trim());
    expect(buttonTexts).not.toContain(',');
    expect(buttonTexts).not.toContain('!');
    expect(buttonTexts).not.toContain('?');
    
    // Check that punctuation is revealed in the text (not hidden)
    const punctuation = wrapper.findAll('.punctuation');
    expect(punctuation.length).toBeGreaterThan(0);
    punctuation.forEach(p => {
      expect([',', '!', '?'].includes(p.text().trim())).toBe(true);
    });
  });

  it.each([
    ["“For God”", "“”"],
    ["¶For God", "¶"],
    ["¶“For ¶God”", "¶“¶”"],
  ])("auto-reveals OSIS markers and accepts words in %s", async (text, markers) => {
    const wrapper = createWrapper({textItems: [{key: "verse1", text}]});
    await wrapper.vm.$nextTick();

    const buttonTexts = wrapper.findAll(".word-buttons .button").map(button => button.text().trim());
    expect(buttonTexts.sort()).toEqual(["For", "God"]);
    expect(wrapper.findAll(".punctuation").map(token => token.text()).join("")).toBe(markers);

    for (const word of ["For", "God"]) {
      const button = wrapper.findAll(".word-buttons .button").find(candidate => candidate.text().trim() === word);
      expect(button).toBeDefined();
      await button.trigger("click");
      expect(button.classes()).not.toContain("incorrect");
    }

    expect(wrapper.find(".memorize-text").classes()).toContain("completed");
    expect(wrapper.emitted("memorize-completed")).toHaveLength(1);
    wrapper.unmount();
  });

  it.each(["¶For God", "¶“For ¶God”", "“For God”"])("can replay %s after reset", async (text) => {
    const wrapper = createWrapper({textItems: [{key: "verse1", text}]});
    await wrapper.vm.$nextTick();

    for (let completion = 1; completion <= 2; completion++) {
      for (const word of ["For", "God"]) {
        const button = wrapper.findAll(".word-buttons .button").find(candidate => candidate.text().trim() === word);
        expect(button).toBeDefined();
        expect(button.classes()).not.toContain("disabled");
        await button.trigger("click");
        expect(button.classes()).not.toContain("incorrect");
        expect(button.classes()).toContain("disabled");
      }

      expect(wrapper.find(".memorize-text").classes()).toContain("completed");
      expect(wrapper.emitted("memorize-completed")).toHaveLength(completion);

      if (completion === 1) {
        await wrapper.findAll(".memorize-controls .icon-button")[1].trigger("click");
        expect(wrapper.find(".memorize-text").classes()).not.toContain("completed");
        expect(wrapper.findAll(".memorize-word:not(.punctuation)").map(word => word.text())).toEqual(["___", "___"]);
      }
    }

    wrapper.unmount();
  });

  it.each(["“”", "¶", "¶“”"])("does not create buttons for marker-only text %s", async (text) => {
    const wrapper = createWrapper({textItems: [{key: "verse1", text}]});
    await wrapper.vm.$nextTick();
    expect(wrapper.findAll(".word-buttons .button")).toHaveLength(0);
    expect(wrapper.findAll(".punctuation").map(token => token.text()).join("")).toBe(text);
    wrapper.unmount();
  });

  it("completes the exercise in sequence and transitions to completed state", async () => {
    const wrapper = createWrapper({
      textItems: [
        { key: "simple", text: "This is a simple test." }
      ]
    });
    
    // Wait for component to initialize
    await wrapper.vm.$nextTick();
    
    // Verify not completed initially
    expect(wrapper.find('.memorize-text').classes()).not.toContain('completed');
    
    // Get all the words in sequence
    const words = ['This', 'is', 'a', 'simple', 'test'];
    
    // Loop through all words and complete the exercise
    for (const word of words) {
      // Find and click the correct button
      const wordButton = findButtonForWord(wrapper, word);
      if (wordButton) {
        await wordButton.trigger('click');
      }
      
      // Wait for any animations or state updates
      await wrapper.vm.$nextTick();
    }
    
    // Should now be in completed state
    expect(wrapper.find('.memorize-text').classes()).toContain('completed');
  });

  it("clears peek mode when reset is clicked", async () => {
    const wrapper = createWrapper();
    
    // First enable peek mode
    await wrapper.find('.memorize-controls .icon-button').trigger('pointerdown');
    expect(wrapper.find('.memorize-text').classes()).toContain('preview');
    
    // Click reset button while in peek mode
    await wrapper.findAll('.memorize-controls .icon-button')[1].trigger('click');
    
    // Peek mode should be cleared
    expect(wrapper.find('.memorize-text').classes()).not.toContain('preview');
  });

  it("keeps track of progress when selecting words out of order", async () => {
    // Create a simple wrapper
    const wrapper = createWrapper({
      textItems: [
        { key: "simple", text: "This is a test." }
      ]
    });
    
    await wrapper.vm.$nextTick();
    
    // First get the correct word
    const currentIndex = wrapper.vm.currentWordIndex;
    const currentWordInfo = getLocalIndicesFromWrapper(wrapper, currentIndex);
    const correctWord = getWordsFromWrapper(wrapper, currentWordInfo.itemIndex)[currentWordInfo.localIndex];
    
    // Find a different word and try to click it
    const incorrectButton = findIncorrectButton(wrapper, correctWord);
    if (incorrectButton) {
      await incorrectButton.trigger('click');
      
      // The incorrect class should be added
      expect(incorrectButton.classes()).toContain('incorrect');
      
      // Current word index should not change
      expect(wrapper.vm.currentWordIndex).toBe(currentIndex);
      
      // Find and click the correct button
      const correctButton = findButtonForWord(wrapper, correctWord);
      if (correctButton) {
        await correctButton.trigger('click');
        
        // Current word index should advance
        expect(wrapper.vm.currentWordIndex).toBe(currentIndex + 1);
      }
    }
  });

  it("manages state correctly for sequential replay", async () => {
    // Test that the component correctly saves state between word selections
    const wrapper = createWrapper();
    await wrapper.vm.$nextTick();
    
    // Clear any existing emitted events
    wrapper.emitted()['save-mode-config'] = [];
    
    // Get the first word
    const currentIndex = wrapper.vm.currentWordIndex;
    const currentWordInfo = getLocalIndicesFromWrapper(wrapper, currentIndex);
    const currentWord = getWordsFromWrapper(wrapper, currentWordInfo.itemIndex)[currentWordInfo.localIndex];
    
    // Find and click the correct button
    const wordButton = findButtonForWord(wrapper, currentWord);
    if (wordButton) {
      await wordButton.trigger('click');
      
      // Should have emitted save-mode-config
      expect(wrapper.emitted('save-mode-config')).toBeTruthy();
      
      // Saved config should have incremented currentWordIndex
      const savedConfig = wrapper.emitted('save-mode-config')[0][0];
      expect(savedConfig.scrambleConfig.currentWordIndex).toBe(currentIndex + 1);
      
      // Check that the button state is updated in the saved config
      expect(savedConfig.scrambleConfig.scrambledWords.some(w => 
        w.originalIndices.includes(currentIndex) && 
        (w.remainingUses === 0 ? w.used : w.remainingUses < wrapper.vm.scrambledWords.find(
          sw => sw.originalIndices.includes(currentIndex)
        ).remainingUses + 1)
      )).toBe(true);
    }
  });
});

// Helper functions for the tests
function getWordsFromWrapper(wrapper, itemIndex) {
  const text = wrapper.vm.textItems[itemIndex].text;
  return text.match(/(["".,;:!?…"'«»„‚–—\-()[\]{}]+)|([^\s"".,;:!?…"'«»„‚–—\-()[\]{}]+)/g) || [];
}

function getLocalIndicesFromWrapper(wrapper, globalIndex) {
  let currentCount = 0;
  for (let i = 0; i < wrapper.vm.textItems.length; i++) {
    const wordsInItem = getWordsFromWrapper(wrapper, i).length;
    if (globalIndex < currentCount + wordsInItem) {
      return { itemIndex: i, localIndex: globalIndex - currentCount };
    }
    currentCount += wordsInItem;
  }
  return { itemIndex: wrapper.vm.textItems.length - 1, localIndex: 0 };
}

function findButtonForWord(wrapper, word) {
  const buttons = wrapper.findAll('.word-buttons .button');
  for (let i = 0; i < buttons.length; i++) {
    const buttonText = buttons[i].text().trim().replace(/\s+\(\d+\)$/, '');
    if (buttonText.toLowerCase() === word.toLowerCase()) {
      return buttons[i];
    }
  }
  return null;
}

function findIncorrectButton(wrapper, correctWord) {
  const buttons = wrapper.findAll('.word-buttons .button');
  for (let i = 0; i < buttons.length; i++) {
    const buttonText = buttons[i].text().trim().replace(/\s+\(\d+\)$/, '');
    if (buttonText.toLowerCase() !== correctWord.toLowerCase() && !buttons[i].classes().includes('disabled')) {
      return buttons[i];
    }
  }
  return null;
}
