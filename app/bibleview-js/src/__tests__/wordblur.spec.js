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
import WordBlur from "@/components/memorize/WordBlur.vue";
import { describe, it, expect } from 'vitest';

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

  // Buttons always in fixed order: [revealAll, revealLast, blur, reset]
  // revealAll and revealLast are disabled when not applicable
  const allButtons = (wrapper) => wrapper.findAll('.icon-button');
  const revealAllButton = (wrapper) => allButtons(wrapper)[0];
  const revealLastButton = (wrapper) => allButtons(wrapper)[1];
  const blurButton = (wrapper) => allButtons(wrapper)[2];
  const resetButton = (wrapper) => allButtons(wrapper)[3];

  it("renders correctly with default props", () => {
    const wrapper = createWrapper();

    expect(wrapper.find('.memorize-controls').exists()).toBe(true);
    expect(wrapper.find('.memorize-text').exists()).toBe(true);
    expect(wrapper.findAll('.memorize-word').length).toBeGreaterThan(0);
    expect(wrapper.findAll('.blurred').length).toBe(0);
  });

  it("increases blur level when the blur button is clicked", async () => {
    const wrapper = createWrapper();

    expect(wrapper.findAll('.blurred').length).toBe(0);

    await blurButton(wrapper).trigger('click');
    const blurredAfterFirst = wrapper.findAll('.blurred').length;
    expect(blurredAfterFirst).toBeGreaterThan(0);

    await blurButton(wrapper).trigger('click');
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(blurredAfterFirst);
  });

  it("resets blur when the reset button is clicked", async () => {
    const wrapper = createWrapper();

    await blurButton(wrapper).trigger('click');
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(0);

    await resetButton(wrapper).trigger('click');
    expect(wrapper.findAll('.blurred').length).toBe(0);
  });

  it("reveals a word temporarily when it's clicked", async () => {
    const wrapper = createWrapper();

    await blurButton(wrapper).trigger('click');
    expect(wrapper.findAll('.blurred').length).toBeGreaterThan(0);

    const blurredWord = wrapper.find('.blurred');
    await blurredWord.trigger('click');

    expect(blurredWord.classes()).toContain('blurred');
    expect(blurredWord.classes()).toContain('revealed');
  });

  it("emits save-mode-config when configuration changes", async () => {
    const wrapper = createWrapper();

    await blurButton(wrapper).trigger('click');

    const emittedEvents = wrapper.emitted('save-mode-config');
    expect(emittedEvents).toBeTruthy();
    expect(emittedEvents[0][0]).toHaveProperty('blurConfig');
    expect(emittedEvents[0][0].blurConfig).toHaveProperty('blurLevel', 1);
  });

  it("shows blur level badge after first blur click", async () => {
    const wrapper = createWrapper();

    // Initially no badge on blur button
    expect(blurButton(wrapper).find('.icon-badge').exists()).toBe(false);

    await blurButton(wrapper).trigger('click');

    // Badge should appear showing next level
    expect(blurButton(wrapper).find('.icon-badge').exists()).toBe(true);
  });

  it("hides blur level badge after reset", async () => {
    const wrapper = createWrapper();

    await blurButton(wrapper).trigger('click');
    expect(blurButton(wrapper).find('.icon-badge').exists()).toBe(true);

    await resetButton(wrapper).trigger('click');
    expect(blurButton(wrapper).find('.icon-badge').exists()).toBe(false);
  });

  it("reveal all button is disabled initially and enabled after blur", async () => {
    const wrapper = createWrapper();

    expect(revealAllButton(wrapper).classes()).toContain('disabled');

    await blurButton(wrapper).trigger('click');

    expect(revealAllButton(wrapper).classes()).not.toContain('disabled');
  });

  it("reveals all blurred words while 'Reveal All' is held down", async () => {
    const wrapper = createWrapper();

    await blurButton(wrapper).trigger('click');

    const blurredCount = wrapper.findAll('.blurred').length;
    expect(blurredCount).toBeGreaterThan(0);

    await revealAllButton(wrapper).trigger('pointerdown');
    expect(wrapper.findAll('.blurred.revealed').length).toBe(blurredCount);

    await revealAllButton(wrapper).trigger('pointerup');
    expect(wrapper.findAll('.revealed').length).toBe(0);
    expect(wrapper.findAll('.blurred').length).toBe(blurredCount);
  });

  it("'Reveal Last' only reveals words from the current blur level", async () => {
    const wrapper = createWrapper();

    // Blur to level 1
    await blurButton(wrapper).trigger('click');
    const blurredAtLevel1 = wrapper.findAll('.blurred').length;

    // Blur to level 2
    await blurButton(wrapper).trigger('click');
    const blurredAtLevel2 = wrapper.findAll('.blurred').length;
    const newlyBlurred = blurredAtLevel2 - blurredAtLevel1;
    expect(newlyBlurred).toBeGreaterThan(0);

    // Hold "Reveal Last" — should only reveal words new at level 2
    await revealLastButton(wrapper).trigger('pointerdown');
    expect(wrapper.findAll('.blurred.revealed').length).toBe(newlyBlurred);

    // Release
    await revealLastButton(wrapper).trigger('pointerup');
    expect(wrapper.findAll('.revealed').length).toBe(0);
  });

  it("'Reveal Last' button is disabled until blur level 2+", async () => {
    const wrapper = createWrapper();

    expect(revealLastButton(wrapper).classes()).toContain('disabled');

    // Level 1: still disabled
    await blurButton(wrapper).trigger('click');
    expect(revealLastButton(wrapper).classes()).toContain('disabled');

    // Level 2: enabled
    await blurButton(wrapper).trigger('click');
    expect(revealLastButton(wrapper).classes()).not.toContain('disabled');
  });

  it("loads existing configuration from props", () => {
    const existingConfig = {
      blurConfig: {
        blurLevel: 3,
        revealedWords: {}
      }
    };

    const wrapper = createWrapper({ modeConfig: existingConfig });

    expect(wrapper.vm.blurLevel).toBe(3);
    expect(wrapper.vm.blurLevel).toBe(existingConfig.blurConfig.blurLevel);
  });
});
