<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div class="memorize-controls">
    <div class="button"
         @touchstart="isPeeking = true"
         @touchend="isPeeking = false"
         @mousedown="isPeeking = true"
         @mouseup="isPeeking = false"
    >
      {{ strings.peek }}
    </div>
    <div @click="resetWords()" class="button">{{ strings.reset }}</div>
  </div>
      
  <!-- Text area with revealed words or full preview -->
  <div class="memorize-text" :class="{ preview: isPeeking, completed: isCompleted }">
    <template v-if="isPeeking">
      <div v-for="item in textItems" :key="item.key" class="text-block">
        <span class="memorize-word">{{ item.text }}</span>
      </div>
    </template>
    <template v-else>
      <div v-for="(item, itemIndex) in textItems" :key="item.key" class="text-block">
        <template v-for="(word, wordIndex) in getWordsFromText(item.text)" :key="`text-${item.key}-${wordIndex}`">
          <span
              class="memorize-word"
              :class="{
                  'revealed': isWordRevealed(getGlobalWordIndex(itemIndex, wordIndex)),
                  'punctuation': isPunctuation(word)
                }"
          >
            {{ isPunctuation(word) || isWordRevealed(getGlobalWordIndex(itemIndex, wordIndex)) ? word : '___' }}
          </span>
        </template>
      </div>
    </template>
  </div>
      
  <!-- Word buttons in scrambled order -->
  <div class="word-buttons">
    <div
        v-for="(wordObj, buttonIndex) in scrambledWords"
        :key="`button-${buttonIndex}`"
        class="button small memorize-button"
        :class="{ 
          incorrect: wordObj.incorrect,
          disabled: wordObj.used,
        }"
        @click="selectWord(buttonIndex, wordObj)"
    >
      {{ wordObj.word }}{{ wordObj.remainingUses > 1 ? ` (${wordObj.remainingUses})` : '' }}
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, computed} from "vue";
import { useCommon } from "@/composables";
import {MemorizeTextItem} from "@/types/documents";

interface WordObject {
    word: string;
    originalIndices: number[];  // Track all global positions where this word appears
    remainingUses: number;     // Track how many more times this word can be used
    used: boolean;             // Word is fully used (all occurrences used)
    incorrect: boolean;
}

interface WordScrambleConfig {
    scrambleConfig?: {
        currentWordIndex: number;
        scrambledWords: WordObject[];
    }
}

const props = defineProps<{ 
    textItems: MemorizeTextItem[],
    modeConfig: WordScrambleConfig | undefined
}>();

const emit = defineEmits<{
    (e: 'save-mode-config', config: WordScrambleConfig): void;
}>();

const { strings } = useCommon();

const scrambledWords = ref<WordObject[]>([]);
const currentWordIndex = ref<number>(0);
const isPeeking = ref<boolean>(false);

const isCompleted = computed(() => {
  if (scrambledWords.value.length === 0) return false;
  return scrambledWords.value.every(word => word.used);
});

// Convert item and word indices to a global word index
function getGlobalWordIndex(itemIndex: number, wordIndex: number): number {
    let globalIndex = wordIndex;
    // Add the length of all previous items' word arrays
    for (let i = 0; i < itemIndex; i++) {
        globalIndex += getWordsFromText(props.textItems[i].text).length;
    }
    return globalIndex;
}

// Get the item and local word indices from a global word index
function getLocalIndices(globalIndex: number): { itemIndex: number, localIndex: number } {
    let currentCount = 0;
    for (let i = 0; i < props.textItems.length; i++) {
        const wordsInItem = getWordsFromText(props.textItems[i].text).length;
        if (globalIndex < currentCount + wordsInItem) {
            return {
                itemIndex: i,
                localIndex: globalIndex - currentCount
            };
        }
        currentCount += wordsInItem;
    }
    // Should never reach here if indices are correct
    return { itemIndex: props.textItems.length - 1, localIndex: 0 };
}

function getWordsFromText(text: string) {
    // Split text into words and punctuation tokens
    // This regex matches:
    // 1. Punctuation: one or more punctuation characters (including quotation marks and Unicode variants)
    // 2. Words: one or more non-whitespace, non-punctuation characters
    const tokens = text.match(/([“”.,;:!?…"'«»„‚–—\-()[\]{}]+)|([^\s“”.,;:!?…"'«»„‚–—\-()[\]{}]+)/g) || [];
    return tokens.filter(token => token.length > 0);
}

function isWordRevealed(globalWordIndex: number) {
    return globalWordIndex < currentWordIndex.value;
}

function isPunctuation(word: string): boolean {
    return /^[“”.,;:!?…"'«»„‚–—\-()[\]{}]+$/.test(word);
}

onMounted(() => {
    const config = props.modeConfig?.scrambleConfig;
    if (config) {
        scrambledWords.value = config.scrambledWords ?? [];
        currentWordIndex.value = config.currentWordIndex ?? 0;
    } else {
        resetWords();
    }
    
    // Make sure we're not starting on a punctuation token
    skipPunctuationTokens();
});

function skipPunctuationTokens() {
    while (currentWordIndex.value < getWordsFromText(props.textItems.map(item => item.text).join(' ')).length) {
        const { itemIndex, localIndex } = getLocalIndices(currentWordIndex.value);
        const word = getWordsFromText(props.textItems[itemIndex].text)[localIndex];
        if (!isPunctuation(word)) {
            break;
        }
        currentWordIndex.value++;
    }
}

function selectWord(buttonIndex: number, wordObj: WordObject) {
    // Check if this is the correct next word
    if (wordObj.originalIndices.includes(currentWordIndex.value)) {
        // Correct word selected
        scrambledWords.value[buttonIndex].remainingUses--;
        scrambledWords.value[buttonIndex].incorrect = false;
        if (scrambledWords.value[buttonIndex].remainingUses === 0) {
            scrambledWords.value[buttonIndex].used = true;
        }
        
        // Advance past the current word
        currentWordIndex.value++;
        
        // Skip any punctuation tokens that follow
        skipPunctuationTokens();
        
        // Save state after successful word selection
        saveState();
    } else {
        // Incorrect word selected
        scrambledWords.value[buttonIndex].incorrect = true;

        // Reset the incorrect status after a short delay
        setTimeout(() => {
            scrambledWords.value[buttonIndex].incorrect = false;
        }, 1000);
    }
}

function saveState() {
    emit('save-mode-config', {
        scrambleConfig: {
            currentWordIndex: currentWordIndex.value,
            scrambledWords: scrambledWords.value
        }
    });
}

function resetWords() {
    // Create a map to track all words across all text items
    const wordMap = new Map<string, { indices: number[], count: number }>();
    
    // Process all text items
    let globalWordIndex = 0;
    
    for (const item of props.textItems) {
        const words = getWordsFromText(item.text);
        
        // Build the word map with all occurrences
        words.forEach((word) => {
            // Skip punctuation - they will be shown directly
            if (isPunctuation(word)) {
                // Still increment the index to maintain alignment with the text
                globalWordIndex++;
                return;
            }
            
            const normalizedWord = word.toLowerCase();
            if (wordMap.has(normalizedWord)) {
                const entry = wordMap.get(normalizedWord)!;
                entry.indices.push(globalWordIndex);
                entry.count++;
            } else {
                wordMap.set(normalizedWord, { indices: [globalWordIndex], count: 1 });
            }
            globalWordIndex++;
        });
    }
    
    // Create unique word objects with their occurrences
    const wordObjects: WordObject[] = [];
    wordMap.forEach(data => {
        // Find a representative word from the original text (preserve casing)
        const firstIndex = data.indices[0];
        const { itemIndex, localIndex } = getLocalIndices(firstIndex);
        const originalWord = getWordsFromText(props.textItems[itemIndex].text)[localIndex];
        
        wordObjects.push({
            word: originalWord,
            originalIndices: data.indices,
            remainingUses: data.count,
            used: false,
            incorrect: false
        });
    });

    // Reset state
    scrambledWords.value = [...wordObjects].sort(() => Math.random() - 0.5);
    currentWordIndex.value = 0;
    isPeeking.value = false;

    // Save the initial state
    saveState();
}

</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.memorize-text {
  transition: border-color 0.3s ease;
  .noAnimation & {
    transition: none;
  }
  .memorize-word {
    margin-right: 4px;
    min-width: 1.5em;
    user-select: none;
    -webkit-user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;
    
    &.punctuation {
      color: var(--primary-color);
      margin-right: 0;
    }
    
    &.revealed {
      color: var(--text-color);
    }
  }
  
  &.preview {
    border: 1px dashed var(--primary-color);
    background-color: rgba(0, 0, 0, 0.03);
    .night & {
      background-color: rgba(255, 255, 255, 0.03);
    }
    padding: 1rem;
  }
  
  &.completed {
    margin-top: 0.5rem;
    margin-bottom: 0.5rem;
    border: 2px solid #28a745;
    border-radius: 8px;
    padding: 1rem;
    background-color: rgba(40, 167, 69, 0.05);
    .night & {
      background-color: rgba(40, 167, 69, 0.1);
    }
    animation: completionPulse 2s;
    .noAnimation & {
      animation: none;
    }
  }
}

.text-block {
  margin-bottom: 1rem;
}

.word-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 1.5rem;

  .memorize-button {
    margin: 2px;
    min-width: auto;
    padding: 8px 12px;
    border-radius: $button-border-radius;
    font-weight: 500;
    transition: all 0.2s ease;
    .noAnimation & {
      transition: none;
    }
    
    &:active {
      transform: translateY(1px);
    }
    
    &.incorrect {
      background-color: #e74c3c;
      animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
      .noAnimation & {
        animation: none;
      }
    }
    
    &.disabled {
      opacity: 0.5;
    }
  }
}

.memorize-controls {
  .button {
    min-width: 100px;
    font-weight: 500;
    
    &:active {
      transform: translateY(1px);
      opacity: 0.9;
    }
  }
}

@keyframes shake {
  10%, 90% {
    transform: translateX(-1px);
  }
  20%, 80% {
    transform: translateX(2px);
  }
  30%, 50%, 70% {
    transform: translateX(-4px);
  }
  40%, 60% {
    transform: translateX(4px);
  }
}

@keyframes completionPulse {
  0% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0.4); }
  70% { box-shadow: 0 0 0 10px rgba(40, 167, 69, 0); }
  100% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0); }
}
</style>