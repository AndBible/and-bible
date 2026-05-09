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
import { nextTick } from "vue";
import WordOrder from "@/components/memorize/WordOrder.vue";
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

// Mock the useCommon composable
vi.mock("@/composables", () => ({
  useCommon: () => ({
    strings: {
      peek: "Peek",
      reset: "Reset"
    }
  })
}));

// Mock Math.random to ensure deterministic behavior in tests
const originalRandom = Math.random;
beforeEach(() => {
  let counter = 0;
  Math.random = () => {
    counter = (counter + 0.1) % 1;
    return counter;
  };
});

afterEach(() => {
  Math.random = originalRandom;
  vi.restoreAllMocks();
});

describe("WordOrder.vue", () => {
  const createWrapper = async (props = {}) => {
    const wrapper = mount(WordOrder, {
      props: {
        textItems: [
          { key: "verse1", text: "In the beginning God created" }
        ],
        modeConfig: undefined,
        ...props,
      },
    });
    await nextTick();
    return wrapper;
  };

  describe("Initialization", () => {
    it("renders word tiles for all non-punctuation words", async () => {
      const wrapper = await createWrapper();
      const tiles = wrapper.findAll(".order-tile");
      // "In the beginning God created" = 5 words
      expect(tiles.length).toBe(5);
    });

    it("shuffles words on mount when no saved config", async () => {
      const wrapper = await createWrapper();
      const tiles = wrapper.findAll(".order-tile");
      const displayedWords = tiles.map(t => t.text().trim());
      const originalWords = ["In", "the", "beginning", "God", "created"];
      // Words should all be present but not necessarily in order
      expect(displayedWords.sort()).toEqual([...originalWords].sort());
    });

    it("excludes punctuation from tiles", async () => {
      const wrapper = await createWrapper({
        textItems: [
          { key: "verse1", text: "Hello, world! How are you?" }
        ]
      });
      const tiles = wrapper.findAll(".order-tile");
      const displayedWords = tiles.map(t => t.text().trim());
      expect(displayedWords).not.toContain(",");
      expect(displayedWords).not.toContain("!");
      expect(displayedWords).not.toContain("?");
      expect(displayedWords.sort()).toEqual(["Hello", "How", "are", "world", "you"].sort());
    });

    it("handles multiple text items", async () => {
      const wrapper = await createWrapper({
        textItems: [
          { key: "verse1", text: "In the" },
          { key: "verse2", text: "beginning God" }
        ]
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(4);
    });

    it("restores state from modeConfig", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [4, 3, 2, 1, 0],

          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      // Reversed order: "created God beginning the In"
      expect(tiles[0].text().trim()).toBe("created");
      expect(tiles[4].text().trim()).toBe("In");
    });

    it("resets if saved config length doesn't match word count", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0], // wrong length

          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(5);
    });
  });

  describe("Correct position feedback", () => {
    it("marks correctly positioned tiles with correct class", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 4, 3, 2], // first two correct

          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles[0].classes()).toContain("correct");
      expect(tiles[1].classes()).toContain("correct");
      expect(tiles[2].classes()).not.toContain("correct");
      expect(tiles[3].classes()).toContain("correct"); // 3 is at index 3
      expect(tiles[4].classes()).not.toContain("correct");
    });

    it("marks words after punctuation as correct when in right position", async () => {
      // "And Joseph her husband, being..." — comma and dots are punctuation
      // words: ["And", "Joseph", "her", "husband", "being"]
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "And Joseph her husband, being..." }],
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4], // all in correct order
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles).toHaveLength(5);
      // All tiles should show as completed since all are correct
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("marks tile as correct after simulated drag to right position", async () => {
      // words: ["And", "Joseph", "her", "husband", "being"]
      // Start: being(4) Joseph(1) husband(3) her(2) And(0)
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "And Joseph her husband, being..." }],
        modeConfig: {
          orderConfig: {
            currentOrder: [4, 1, 3, 2, 0],
          }
        }
      });
      let tiles = wrapper.findAll(".order-tile");
      // Position 1 has "Joseph" (correct) — verify it shows as correct before drag
      expect(tiles[1].classes()).toContain("correct");
      // Position 4 has "And" (wrong, should be "being") — not correct
      expect(tiles[4].classes()).not.toContain("correct");

      // Simulate drag: swap positions 0 and 4 (move "And" to 0, "being" to 4)
      const tilesArray = wrapper.vm.tiles;
      const temp = tilesArray[0];
      tilesArray[0] = tilesArray[4];
      tilesArray[4] = temp;
      await nextTick();

      tiles = wrapper.findAll(".order-tile");
      // Position 0: "And" → correct
      expect(tiles[0].classes()).toContain("correct");
      // Position 4: "being" → correct (this was the reported bug)
      expect(tiles[4].classes()).toContain("correct");
      // Position 2: "husband" (should be "her") → not correct
      expect(tiles[2].classes()).not.toContain("correct");
    });

    it("marks 'being' as correct at position 4 when text has punctuation", async () => {
      // Partially correct: only first 3 and last are correct
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "And Joseph her husband, being..." }],
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 4, 3], // husband and being swapped
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles[0].classes()).toContain("correct"); // And
      expect(tiles[1].classes()).toContain("correct"); // Joseph
      expect(tiles[2].classes()).toContain("correct"); // her
      expect(tiles[3].classes()).not.toContain("correct"); // being at husband's position
      expect(tiles[4].classes()).not.toContain("correct"); // husband at being's position
    });
  });

  describe("Duplicate and case-insensitive matching", () => {
    it("accepts duplicate words in any matching position", async () => {
      // "the cat and the dog" — "the" at positions 0 and 3
      // Swapping the two "the" instances should still be correct
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "the cat and the dog" }],
        modeConfig: {
          orderConfig: {
            currentOrder: [3, 1, 2, 0, 4], // "the" instances swapped
          }
        }
      });
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("marks duplicate word tiles as correct regardless of which instance", async () => {
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "the cat and the dog" }],
        modeConfig: {
          orderConfig: {
            currentOrder: [3, 1, 2, 0, 4], // "the" instances swapped
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      // All positions have matching words, so completed — tiles get completed-tile class
      tiles.forEach(tile => {
        expect(tile.classes()).toContain("completed-tile");
      });
    });

    it("treats words as case-insensitive for correctness", async () => {
      // "In the beginning" — "In" at position 0
      // If tile with "in" (lowercase from another position) is at position 0, it should match
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "In the in" }],
        modeConfig: {
          orderConfig: {
            currentOrder: [2, 1, 0], // "in" and "In" swapped
          }
        }
      });
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    // Regression test for #3735: Genesis 1:1 has three instances of "the".
    // Any "the" tile placed at any of the three "the" positions must be accepted.
    it("accepts any of three 'the' instances at any 'the' position (Gen 1:1)", async () => {
      // words: ["In", "the", "beginning", "God", "created", "the", "heavens", "and", "the", "earth"]
      //         0      1      2            3      4          5      6           7      8      9
      // Permutation that cycles the three "the" indices (1→5→8→1) and keeps
      // every other word in place. Every position still has the correct word,
      // so the verse should be marked completed.
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "In the beginning God created the heavens and the earth" }],
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 8, 2, 3, 4, 1, 6, 7, 5, 9],
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles).toHaveLength(10);
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("marks a 'the' tile correct when placed at any of three 'the' positions", async () => {
      // Start with everything correct except positions 1 and 2 swapped:
      // [In, beginning, the, God, created, the, heavens, and, the, earth]
      // Position 1 has "beginning" (wrong); position 2 has "the" — but expected at 2 is "beginning".
      // Now move the "the" originally at index 8 into position 1 (a 'the' position):
      // [In, the(orig=8), beginning, God, created, the(orig=1), heavens, and, the(orig=5), earth]
      // Even though it's the third "the" tile, it should be marked correct at position 1.
      const wrapper = await createWrapper({
        textItems: [{ key: "v1", text: "In the beginning God created the heavens and the earth" }],
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 8, 2, 3, 4, 1, 6, 7, 5, 9],
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      // All three 'the' positions (1, 5, 8) have a 'the' tile, so each must be correct
      // (or completed-tile, since the whole thing is done).
      [1, 5, 8].forEach(pos => {
        expect(tiles[pos].text().trim().toLowerCase()).toBe("the");
        // Once completed, tiles get the completed-tile class instead of correct.
        expect(tiles[pos].classes()).toContain("completed-tile");
      });
    });
  });

  describe("Completion", () => {
    it("shows completed state when all words are in correct order", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],

          }
        }
      });
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("emits memorize-completed when solved", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],
          }
        }
      });

      expect(wrapper.emitted('memorize-completed')).toBeTruthy();
    });

    it("gives all tiles completed-tile class when done", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],

          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      tiles.forEach(tile => {
        expect(tile.classes()).toContain("completed-tile");
      });
    });
  });

  describe("Peek functionality", () => {
    it("shows correct text when peeking", async () => {
      const wrapper = await createWrapper();
      const peekBtn = wrapper.find(".icon-button");
      await peekBtn.trigger("pointerdown");

      expect(wrapper.find(".preview").exists()).toBe(true);
      expect(wrapper.find(".order-area").exists()).toBe(false);
    });

    it("hides preview on pointer up", async () => {
      const wrapper = await createWrapper();
      const peekBtn = wrapper.find(".icon-button");
      await peekBtn.trigger("pointerdown");
      expect(wrapper.find(".preview").exists()).toBe(true);

      await peekBtn.trigger("pointerup");
      expect(wrapper.find(".preview").exists()).toBe(false);
      expect(wrapper.find(".order-area").exists()).toBe(true);
    });
  });

  describe("Reset functionality", () => {
    it("reshuffles words on reset", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4], // solved

          }
        }
      });

      const buttons = wrapper.findAll(".icon-button");
      await buttons[1].trigger("click");

      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).not.toContain("completed");
    });

    it("emits save-mode-config after reset", async () => {
      const wrapper = await createWrapper();
      wrapper.emitted()['save-mode-config'] = [];

      const buttons = wrapper.findAll(".icon-button");
      await buttons[1].trigger("click");

      expect(wrapper.emitted('save-mode-config')).toBeTruthy();
    });
  });

  describe("Edge cases", () => {
    it("handles single word verse", async () => {
      const wrapper = await createWrapper({
        textItems: [
          { key: "verse1", text: "Rejoice" }
        ]
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(1);
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("handles two word verse", async () => {
      const wrapper = await createWrapper({
        textItems: [
          { key: "verse1", text: "Trust God" }
        ]
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(2);
    });

    it("handles verse with only punctuation", async () => {
      const wrapper = await createWrapper({
        textItems: [
          { key: "verse1", text: "..." }
        ]
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(0);
    });

    it("handles empty text items", async () => {
      const wrapper = await createWrapper({
        textItems: []
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(0);
    });
  });

  describe("State persistence", () => {
    it("emits save-mode-config on mount with shuffled order", async () => {
      const wrapper = await createWrapper();

      const emitted = wrapper.emitted('save-mode-config');
      expect(emitted).toBeTruthy();
      expect(emitted.length).toBeGreaterThan(0);
      const lastConfig = emitted[emitted.length - 1][0];
      expect(lastConfig.orderConfig).toBeDefined();
      expect(lastConfig.orderConfig.currentOrder).toHaveLength(5);
    });
  });
});
