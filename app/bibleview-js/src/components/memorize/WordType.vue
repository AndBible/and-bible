<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div>
    <div class="memorize-controls">
      <div class="controls-right">
        <div class="icon-button" @click="resetWords">
          <FontAwesomeIcon :icon="faUndo"/>
        </div>
        <div class="settings-wrapper" ref="settingsWrapper">
        <div class="settings-trigger" @click="toggleSettings">
          <FontAwesomeIcon :icon="faGear"/>
        </div>
        <div v-if="settingsOpen" class="settings-popup">
          <label class="settings-item">
            <input type="checkbox" v-model="typeEverything" />
            {{ strings.typeEverything }}
          </label>
          <label class="settings-item">
            <input type="checkbox" v-model="errorHeatmap" />
            {{ strings.errorHeatmap }}
          </label>
          <div class="settings-item settings-group">
            <div class="settings-label">{{ strings.wordVisibility }}</div>
            <div class="visibility-options">
              <label v-for="opt in visibilityOptions" :key="opt.value" class="visibility-option">
                <input type="radio" :value="opt.value" v-model="wordVisibility" />
                {{ opt.label }}
              </label>
            </div>
          </div>
        </div>
        </div>
      </div>
    </div>

    <div class="memorize-text type-text"
         :class="{ completed: isCompleted }"
         @click="focusInput"
    >
      <div v-if="!isFocused && !isCompleted && currentWordIndex === 0" class="tap-hint">
        {{ strings.tapToStartTyping }}
      </div>
      <div v-for="(item, itemIndex) in textItems" :key="item.key" class="text-block">
        <template v-for="(token, tokenIndex) in getWordsFromText(item.text)" :key="`${item.key}-${tokenIndex}`">
          <span
              :ref="el => setWordRef(getGlobalWordIndex(itemIndex, tokenIndex), el)"
              class="memorize-word type-word"
              :class="[getWordClass(getGlobalWordIndex(itemIndex, tokenIndex), token), getHeatmapClass(getGlobalWordIndex(itemIndex, tokenIndex))]"
          >{{ token }}</span>
          <span v-if="typeEverything && isCurrent(getGlobalWordIndex(itemIndex, tokenIndex)) && !isPunctuation(token)"
                class="type-buffer">{{ typedBuffer }}</span>
        </template>
      </div>
    </div>

    <!-- Hidden input for keyboard capture -->
    <input ref="hiddenInput"
           class="type-hidden-input"
           type="text"
           autocomplete="off"
           autocapitalize="off"
           autocorrect="off"
           @input="onInput"
           @keydown="onKeydown"
           @focus="isFocused = true"
           @blur="isFocused = false"
    />
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch, nextTick, inject} from "vue";
import {useCommon} from "@/composables";
import {MemorizeTextItem, WordVisibility} from "@/types/documents";
import {readingProgressSettingsKey} from "@/types/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faGear, faUndo} from "@fortawesome/free-solid-svg-icons";

interface WordTypeConfig {
    typeConfig?: {
        currentWordIndex: number;
        typeEverything: boolean;
        wordVisibility: WordVisibility;
        errorHeatmap: boolean;
        errorCounts: Record<number, number>;
    }
}

const props = defineProps<{
    textItems: MemorizeTextItem[]
    modeConfig: WordTypeConfig | undefined
}>();

const emit = defineEmits<{
    (e: 'save-mode-config', config: WordTypeConfig): void;
    (e: 'memorize-completed'): void;
}>();

const {settings: globalSettings, updateSettings} = inject(readingProgressSettingsKey)!;
const {strings} = useCommon();

const currentWordIndex = ref(0);
const typeEverything = ref(false);
const wordVisibility = ref<WordVisibility>('light');
const errorHeatmap = ref(true);
const errorCounts = ref<Record<number, number>>({});
const typedBuffer = ref('');
const incorrectIndex = ref<number | null>(null);
const isFocused = ref(false);
const hiddenInput = ref<HTMLInputElement | null>(null);
const wordRefs = ref<Record<number, Element | null>>({});
const settingsOpen = ref(false);
const settingsWrapper = ref<HTMLElement | null>(null);

const visibilityOptions = computed(() => [
    {value: 'light' as WordVisibility, label: strings.wordVisibilityLight},
    {value: 'dim' as WordVisibility, label: strings.wordVisibilityDim},
    {value: 'hidden' as WordVisibility, label: strings.wordVisibilityHidden},
]);

const totalWords = computed(() => {
    let count = 0;
    for (const item of props.textItems) {
        count += getWordsFromText(item.text).length;
    }
    return count;
});

const isCompleted = computed(() => {
    return currentWordIndex.value >= totalWords.value && totalWords.value > 0;
});

watch(isCompleted, (completed) => {
    if (completed) emit('memorize-completed');
});

watch(typeEverything, (val) => {
    typedBuffer.value = '';
    saveState();
    updateSettings({memorizeTypeFullWords: val});
});

watch(wordVisibility, (val) => {
    saveState();
    updateSettings({memorizeWordVisibility: val});
});

watch(errorHeatmap, (val) => {
    saveState();
    updateSettings({memorizeErrorHeatmap: val});
});

watch(globalSettings, (globals) => {
    typeEverything.value = globals.memorizeTypeFullWords;
    wordVisibility.value = globals.memorizeWordVisibility;
    errorHeatmap.value = globals.memorizeErrorHeatmap;
});

function toggleSettings() {
    settingsOpen.value = !settingsOpen.value;
}

function onClickOutsideSettings(e: Event) {
    if (settingsWrapper.value && !settingsWrapper.value.contains(e.target as Node)) {
        settingsOpen.value = false;
    }
}

function setWordRef(globalIndex: number, el: any) {
    wordRefs.value[globalIndex] = el;
}

// An apostrophe (straight or curly) sitting between two word characters is part of a
// contraction/possessive (e.g. "Lord's", "don't") and must not split the word into
// separate tokens - otherwise typing the word normally is reported as an error. A bare
// trailing apostrophe is also kept attached when the word ends in "s" (e.g. "Jesus'",
// "years'") since that is the standard classical/plural possessive form; a trailing
// apostrophe after any other letter is treated as a closing quote instead.
function getWordsFromText(text: string) {
    const tokens = text.match(/(["".,;:!?…"'«»„‚–—\-()[\]{}]+)|([^\s"".,;:!?…"'«»„‚–—\-()[\]{}]+(?:['’‘][^\s"".,;:!?…"'«»„‚–—\-()[\]{}]+)*(?:(?<=[sS])['’‘])?)/g) || [];
    return tokens.filter(token => token.length > 0);
}

function isPunctuation(word: string): boolean {
    return /^["".,;:!?…"'«»„‚–—\-()[\]{}]+$/.test(word);
}

// Module text may use a curly apostrophe (') while the keyboard produces a straight one -
// normalize both to the same character before comparing typed input to the reference word.
function normalizeApostrophes(word: string): string {
    return word.replace(/[’‘]/g, "'");
}

function getGlobalWordIndex(itemIndex: number, wordIndex: number): number {
    let globalIndex = wordIndex;
    for (let i = 0; i < itemIndex; i++) {
        globalIndex += getWordsFromText(props.textItems[i].text).length;
    }
    return globalIndex;
}

function getLocalIndices(globalIndex: number): { itemIndex: number, localIndex: number } {
    let currentCount = 0;
    for (let i = 0; i < props.textItems.length; i++) {
        const wordsInItem = getWordsFromText(props.textItems[i].text).length;
        if (globalIndex < currentCount + wordsInItem) {
            return {itemIndex: i, localIndex: globalIndex - currentCount};
        }
        currentCount += wordsInItem;
    }
    return {itemIndex: props.textItems.length - 1, localIndex: 0};
}

function getWordAt(globalIndex: number): string {
    const {itemIndex, localIndex} = getLocalIndices(globalIndex);
    return getWordsFromText(props.textItems[itemIndex].text)[localIndex];
}

function isCurrent(globalIndex: number): boolean {
    return globalIndex === currentWordIndex.value;
}

function getHeatmapClass(globalIndex: number): string | undefined {
    if (!errorHeatmap.value) return undefined;
    const count = errorCounts.value[globalIndex];
    if (!count) return undefined;
    return `heatmap-${Math.min(count, 3)}`;
}

function getWordClass(globalIndex: number, token: string) {
    const vis = `visibility-${wordVisibility.value}`;
    if (isPunctuation(token)) {
        const passed = globalIndex < currentWordIndex.value;
        return {'punctuation': true, 'type-correct': passed, 'type-unreached': !passed, [vis]: !passed};
    }
    if (globalIndex < currentWordIndex.value) {
        return {'type-correct': true};
    }
    if (globalIndex === incorrectIndex.value) {
        return {'type-incorrect': true, 'type-current': true, [vis]: true};
    }
    if (globalIndex === currentWordIndex.value) {
        return {'type-current': true, [vis]: true};
    }
    return {'type-unreached': true, [vis]: true};
}

function skipPunctuationTokens() {
    while (currentWordIndex.value < totalWords.value) {
        const word = getWordAt(currentWordIndex.value);
        if (!isPunctuation(word)) break;
        currentWordIndex.value++;
    }
}

function scrollToCurrentWord() {
    nextTick(() => {
        const el = wordRefs.value[currentWordIndex.value];
        if (el && (el as HTMLElement).scrollIntoView) {
            (el as HTMLElement).scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
    });
}

function advanceWord() {
    const idx = currentWordIndex.value;
    if (errorCounts.value[idx]) {
        errorCounts.value[idx]--;
        if (errorCounts.value[idx] === 0) delete errorCounts.value[idx];
    }
    currentWordIndex.value++;
    skipPunctuationTokens();
    typedBuffer.value = '';
    saveState();
    scrollToCurrentWord();
}

function showIncorrect() {
    const idx = currentWordIndex.value;
    incorrectIndex.value = idx;
    errorCounts.value[idx] = Math.min((errorCounts.value[idx] ?? 0) + 1, 3);
    typedBuffer.value = '';
    saveState();
    setTimeout(() => {
        incorrectIndex.value = null;
    }, 500);
}

function onInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const value = input.value;
    input.value = '';

    if (isCompleted.value || value.length === 0) return;

    const currentWord = normalizeApostrophes(getWordAt(currentWordIndex.value));

    if (typeEverything.value) {
        // In "type everything" mode, space/enter submits the current buffer
        if (value === ' ' || value === '\n') {
            if (typedBuffer.value.toLowerCase() === currentWord.toLowerCase()) {
                advanceWord();
            } else {
                showIncorrect();
            }
            return;
        }
        // Accumulate typed characters
        const newBuffer = typedBuffer.value + value;
        // Check if the buffer still matches the prefix of the word
        if (currentWord.toLowerCase().startsWith(newBuffer.toLowerCase())) {
            typedBuffer.value = newBuffer;
            // Auto-advance if fully typed
            if (newBuffer.length === currentWord.length) {
                advanceWord();
            }
        } else {
            showIncorrect();
        }
    } else {
        // First-letter mode: compare just the first character typed
        const typedChar = value.charAt(0);
        if (typedChar.toLowerCase() === currentWord.charAt(0).toLowerCase()) {
            advanceWord();
        } else {
            showIncorrect();
        }
    }
}

function onKeydown(event: KeyboardEvent) {
    if (typeEverything.value && event.key === 'Backspace') {
        event.preventDefault();
        if (typedBuffer.value.length > 0) {
            typedBuffer.value = typedBuffer.value.slice(0, -1);
        }
    }
}

function focusInput() {
    hiddenInput.value?.focus();
}

function resetWords() {
    currentWordIndex.value = 0;
    typedBuffer.value = '';
    incorrectIndex.value = null;
    skipPunctuationTokens();
    saveState();
}

function saveState() {
    emit('save-mode-config', {
        typeConfig: {
            currentWordIndex: currentWordIndex.value,
            typeEverything: typeEverything.value,
            wordVisibility: wordVisibility.value,
            errorHeatmap: errorHeatmap.value,
            errorCounts: errorCounts.value,
        }
    });
}

onMounted(() => {
    const config = props.modeConfig?.typeConfig;
    currentWordIndex.value = config?.currentWordIndex ?? 0;
    typeEverything.value = config?.typeEverything ?? globalSettings.memorizeTypeFullWords;
    wordVisibility.value = config?.wordVisibility ?? globalSettings.memorizeWordVisibility;
    errorHeatmap.value = config?.errorHeatmap ?? globalSettings.memorizeErrorHeatmap;
    errorCounts.value = config?.errorCounts ?? {};
    skipPunctuationTokens();
    window.addEventListener('click', onClickOutsideSettings);
});

onBeforeUnmount(() => {
    window.removeEventListener('click', onClickOutsideSettings);
});
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.type-text {
  position: relative;
  cursor: text;
  min-height: 3em;
  transition: border-color 0.3s ease;
  .noAnimation & {
    transition: none;
  }
  .monochrome & {
    background-color: white;
    border: 1px solid black;
    border-radius: 8px;
    padding: 1rem;
  }
  .monochrome.night & {
    background-color: black;
    border-color: white;
  }

  &.completed {
    margin-top: 0.5rem;
    margin-bottom: 0.5rem;
    border: 2px solid #28a745;
    border-radius: 8px;
    padding: 1rem;
    background-color: rgba(40, 167, 69, 0.05);
    .monochrome & {
      background-color: transparent;
      border-color: black;
    }
    .night & {
      background-color: rgba(40, 167, 69, 0.1);
    }
    .monochrome.night & {
      background-color: transparent;
      border-color: white;
    }
    animation: completionPulse 2s;
    .noAnimation & {
      animation: none;
    }
  }
}

.tap-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--noise-text-color, rgba(0, 0, 0, 0.3));
  font-style: italic;
  pointer-events: none;
  .night & {
    color: rgba(255, 255, 255, 0.3);
  }
}

.type-word {
  margin-right: 4px;
  user-select: none;
  -webkit-user-select: none;
  border-radius: 3px;
  transition: color 0.2s ease, background-color 0.2s ease;
  .noAnimation & {
    transition: none;
  }

  &.type-unreached, &.type-current {
    &.visibility-light {
      color: rgba(0, 0, 0, 0.2);
      .night & { color: rgba(255, 255, 255, 0.2); }
      .monochrome & { color: rgba(0, 0, 0, 0.25); }
      .monochrome.night & { color: rgba(255, 255, 255, 0.25); }
    }
    &.visibility-dim {
      color: rgba(0, 0, 0, 0.08);
      .night & { color: rgba(255, 255, 255, 0.08); }
      .monochrome & { color: rgba(0, 0, 0, 0.1); }
      .monochrome.night & { color: rgba(255, 255, 255, 0.1); }
    }
    &.visibility-hidden {
      color: transparent;
    }
  }

  &.type-current {
    text-decoration: underline;
    text-underline-offset: 3px;
    text-decoration-thickness: 2px;
  }

  &.type-correct {
    color: var(--text-color, inherit);
  }

  &.type-incorrect {
    color: #e74c3c;
    .monochrome & {
      color: inherit;
      text-decoration: line-through;
      font-weight: bold;
    }
    animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
    .noAnimation & {
      animation: none;
    }
  }

  &.punctuation {
    margin-right: 0;
  }

  &.heatmap-1 { background-color: rgba(231, 76, 60, 0.2); }
  &.heatmap-2 { background-color: rgba(231, 76, 60, 0.4); }
  &.heatmap-3 { background-color: rgba(231, 76, 60, 0.6); }

  .monochrome & {
    &.heatmap-1 { background-color: rgba(0, 0, 0, 0.15); }
    &.heatmap-2 { background-color: rgba(0, 0, 0, 0.3); }
    &.heatmap-3 { background-color: rgba(0, 0, 0, 0.45); }
  }
  .monochrome.night & {
    &.heatmap-1 { background-color: rgba(255, 255, 255, 0.15); }
    &.heatmap-2 { background-color: rgba(255, 255, 255, 0.3); }
    &.heatmap-3 { background-color: rgba(255, 255, 255, 0.45); }
  }
}

.type-buffer {
  font-weight: bold;
  color: var(--primary-color, #2196F3);
  border-bottom: 2px solid currentColor;
  margin-left: -2px;
  margin-right: 4px;
}

.type-hidden-input {
  position: absolute;
  opacity: 0;
  height: 0;
  width: 0;
  pointer-events: none;
}

.controls-right {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.settings-wrapper {
  position: relative;
}

.settings-trigger {
  cursor: pointer;
  padding: 6px 10px;
  color: #666;
  font-size: 16px;
  .night & { color: #999; }
  .monochrome & { color: black; }
  .monochrome.night & { color: white; }
}

.settings-popup {
  position: absolute;
  right: 0;
  top: 100%;
  background: var(--background-color);
  border: 1px solid rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
  min-width: 180px;
  padding: 8px 0;
  animation: settings-fade 0.15s ease;
  .night & {
    border-color: rgba(255, 255, 255, 0.3);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  }
  .monochrome & {
    border-color: black;
    box-shadow: none;
  }
  .monochrome.night & {
    border-color: white;
  }
  .noAnimation & {
    animation: none;
  }
}

@keyframes settings-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.settings-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 0.9em;
  user-select: none;

  &.settings-group {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    cursor: default;
  }
}

.settings-label {
  font-size: 0.85em;
  color: #666;
  .night & { color: #999; }
  .monochrome & { color: black; }
  .monochrome.night & { color: white; }
}

.visibility-options {
  display: flex;
  gap: 10px;
}

.visibility-option {
  display: flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  font-size: 0.9em;
}

.text-block {
  margin-bottom: 1rem;
}

.memorize-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

@keyframes shake {
  10%, 90% { transform: translateX(-1px); }
  20%, 80% { transform: translateX(2px); }
  30%, 50%, 70% { transform: translateX(-4px); }
  40%, 60% { transform: translateX(4px); }
}

@keyframes completionPulse {
  0% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0.4); }
  70% { box-shadow: 0 0 0 10px rgba(40, 167, 69, 0); }
  100% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0); }
}
</style>
