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
      <label class="type-everything-toggle">
        <input type="checkbox" v-model="typeEverything" /> {{ strings.typeEverything }}
      </label>
      <div class="button" @click="resetWords">{{ strings.reset }}</div>
    </div>

    <div class="memorize-text type-text"
         :class="{ completed: isCompleted }"
         @click="focusInput"
    >
      <div v-if="!isFocused && !isCompleted && currentWordIndex === 0" class="tap-hint">
        {{ strings.tapToStartTyping }}
      </div>
      <div v-for="(item, itemIndex) in textItems" :key="item.key" class="text-block" :class="{ hidden: !isFocused && !isCompleted && currentWordIndex === 0 }">
        <template v-for="(token, tokenIndex) in getWordsFromText(item.text)" :key="`${item.key}-${tokenIndex}`">
          <span
              :ref="el => setWordRef(getGlobalWordIndex(itemIndex, tokenIndex), el)"
              class="memorize-word type-word"
              :class="getWordClass(getGlobalWordIndex(itemIndex, tokenIndex), token)"
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
import {ref, computed, onMounted, watch, nextTick} from "vue";
import {useCommon} from "@/composables";
import {MemorizeTextItem} from "@/types/documents";

interface WordTypeConfig {
    typeConfig?: {
        currentWordIndex: number;
        typeEverything: boolean;
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

const {strings} = useCommon();

const currentWordIndex = ref(0);
const typeEverything = ref(false);
const typedBuffer = ref('');
const incorrectIndex = ref<number | null>(null);
const isFocused = ref(false);
const hiddenInput = ref<HTMLInputElement | null>(null);
const wordRefs = ref<Record<number, Element | null>>({});

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

watch(typeEverything, () => {
    typedBuffer.value = '';
    saveState();
});

function setWordRef(globalIndex: number, el: any) {
    wordRefs.value[globalIndex] = el;
}

function getWordsFromText(text: string) {
    const tokens = text.match(/(["".,;:!?…"'«»„‚–—\-()[\]{}]+)|([^\s"".,;:!?…"'«»„‚–—\-()[\]{}]+)/g) || [];
    return tokens.filter(token => token.length > 0);
}

function isPunctuation(word: string): boolean {
    return /^["".,;:!?…"'«»„‚–—\-()[\]{}]+$/.test(word);
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

function getWordClass(globalIndex: number, token: string) {
    if (isPunctuation(token)) {
        return {'punctuation': true, 'type-correct': globalIndex < currentWordIndex.value};
    }
    if (globalIndex < currentWordIndex.value) {
        return {'type-correct': true};
    }
    if (globalIndex === incorrectIndex.value) {
        return {'type-incorrect': true, 'type-current': true};
    }
    if (globalIndex === currentWordIndex.value) {
        return {'type-current': true};
    }
    return {'type-unreached': true};
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
    currentWordIndex.value++;
    skipPunctuationTokens();
    typedBuffer.value = '';
    saveState();
    scrollToCurrentWord();
}

function showIncorrect() {
    incorrectIndex.value = currentWordIndex.value;
    typedBuffer.value = '';
    setTimeout(() => {
        incorrectIndex.value = null;
    }, 500);
}

function onInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const value = input.value;
    input.value = '';

    if (isCompleted.value || value.length === 0) return;

    const currentWord = getWordAt(currentWordIndex.value);

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
        }
    });
}

onMounted(() => {
    const config = props.modeConfig?.typeConfig;
    if (config) {
        currentWordIndex.value = config.currentWordIndex ?? 0;
        typeEverything.value = config.typeEverything ?? false;
    }
    skipPunctuationTokens();
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
  transition: color 0.2s ease;
  .noAnimation & {
    transition: none;
  }

  &.type-unreached {
    color: rgba(0, 0, 0, 0.2);
    .night & {
      color: rgba(255, 255, 255, 0.2);
    }
    .monochrome & {
      color: rgba(0, 0, 0, 0.25);
    }
    .monochrome.night & {
      color: rgba(255, 255, 255, 0.25);
    }
  }

  &.type-current {
    text-decoration: underline;
    text-underline-offset: 3px;
    text-decoration-thickness: 2px;
    color: rgba(0, 0, 0, 0.2);
    .night & {
      color: rgba(255, 255, 255, 0.2);
    }
    .monochrome & {
      color: rgba(0, 0, 0, 0.25);
    }
    .monochrome.night & {
      color: rgba(255, 255, 255, 0.25);
    }
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

.type-everything-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
  font-size: 0.9em;
  margin-right: 8px;
}

.text-block {
  margin-bottom: 1rem;

  &.hidden {
    visibility: hidden;
  }
}

.memorize-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;

  .button {
    min-width: 100px;
    font-weight: 500;

    &:active {
      transform: translateY(1px);
      opacity: 0.9;
      .monochrome & {
        opacity: 1;
      }
    }
  }
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
