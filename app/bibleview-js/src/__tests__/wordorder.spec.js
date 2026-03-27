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
            selectedIndex: null,
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
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      expect(tiles.length).toBe(5);
    });
  });

  describe("Tap-to-swap interaction", () => {
    it("selects a tile on first tap", async () => {
      const wrapper = await createWrapper();
      const tiles = wrapper.findAll(".order-tile");
      await tiles[0].trigger("click");
      expect(tiles[0].classes()).toContain("selected");
    });

    it("deselects on tapping the same tile", async () => {
      const wrapper = await createWrapper();
      const tiles = wrapper.findAll(".order-tile");
      await tiles[0].trigger("click");
      expect(tiles[0].classes()).toContain("selected");
      await tiles[0].trigger("click");
      expect(tiles[0].classes()).not.toContain("selected");
    });

    it("swaps two tiles on second tap", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      const firstWord = tiles[0].text().trim();
      const secondWord = tiles[1].text().trim();

      await tiles[0].trigger("click");
      await tiles[1].trigger("click");

      const tilesAfter = wrapper.findAll(".order-tile");
      expect(tilesAfter[0].text().trim()).toBe(secondWord);
      expect(tilesAfter[1].text().trim()).toBe(firstWord);
    });

    it("clears selection after swap", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      await tiles[0].trigger("click");
      await tiles[1].trigger("click");

      const tilesAfter = wrapper.findAll(".order-tile");
      tilesAfter.forEach(tile => {
        expect(tile.classes()).not.toContain("selected");
      });
    });

    it("emits save-mode-config after swap", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      await tiles[0].trigger("click");
      await tiles[1].trigger("click");

      const emitted = wrapper.emitted('save-mode-config');
      expect(emitted).toBeTruthy();
      expect(emitted.length).toBeGreaterThan(0);
      const lastConfig = emitted[emitted.length - 1][0];
      expect(lastConfig.orderConfig).toBeDefined();
      expect(lastConfig.orderConfig.currentOrder).toBeDefined();
    });
  });

  describe("Correct position feedback", () => {
    it("marks correctly positioned tiles with correct class", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 4, 3, 2], // first two correct
            selectedIndex: null,
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
  });

  describe("Completion", () => {
    it("shows completed state when all words are in correct order", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).toContain("completed");
    });

    it("emits memorize-completed when solved", async () => {
      // Start with almost solved - just positions 0 and 1 swapped
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });

      const tiles = wrapper.findAll(".order-tile");
      // Swap positions 0 and 1 to solve
      await tiles[0].trigger("click");
      await tiles[1].trigger("click");

      expect(wrapper.emitted('memorize-completed')).toBeTruthy();
    });

    it("does not allow tile interaction when completed", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      await tiles[0].trigger("click");
      // Should not select
      expect(tiles[0].classes()).not.toContain("selected");
    });

    it("gives all tiles completed-tile class when done", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [0, 1, 2, 3, 4],
            selectedIndex: null,
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
            selectedIndex: null,
          }
        }
      });

      const buttons = wrapper.findAll(".icon-button");
      await buttons[1].trigger("click");

      const orderArea = wrapper.find(".order-area");
      expect(orderArea.classes()).not.toContain("completed");
    });

    it("clears selection on reset", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: 2,
          }
        }
      });

      const buttons = wrapper.findAll(".icon-button");
      await buttons[1].trigger("click");

      const tiles = wrapper.findAll(".order-tile");
      tiles.forEach(tile => {
        expect(tile.classes()).not.toContain("selected");
      });
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
    it("saves currentOrder as array of origIdx values", async () => {
      const wrapper = await createWrapper({
        modeConfig: {
          orderConfig: {
            currentOrder: [1, 0, 2, 3, 4],
            selectedIndex: null,
          }
        }
      });
      const tiles = wrapper.findAll(".order-tile");
      // Swap first two to solve
      await tiles[0].trigger("click");
      await tiles[1].trigger("click");

      const emitted = wrapper.emitted('save-mode-config');
      const lastConfig = emitted[emitted.length - 1][0];
      expect(lastConfig.orderConfig.currentOrder).toEqual([0, 1, 2, 3, 4]);
    });
  });
});
