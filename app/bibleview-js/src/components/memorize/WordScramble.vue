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
  <div>
    <div class="memorize-controls">
      <button
          @touchstart="isPeeking = true"
          @touchend="isPeeking = false"
          class="memorize-button utility"
      >
        {{ strings.peek }}
      </button>
      <button @click="resetWords()" class="memorize-button secondary">{{ strings.reset }}</button>
    </div>
      
    <!-- Text area with revealed words or full preview -->
    <div class="memorize-text" :class="{ 'preview': isPeeking }">
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
                :class="{ 'revealed': isWordRevealed(getGlobalWordIndex(itemIndex, wordIndex)) }"
            >
              {{ isWordRevealed(getGlobalWordIndex(itemIndex, wordIndex)) ? word : '___' }}
            </span>
          </template>
        </div>
      </template>
    </div>
      
    <!-- Word buttons in scrambled order -->
    <div class="word-buttons">
      <button 
        v-for="(wordObj, buttonIndex) in scrambledWords"
        :key="`button-${buttonIndex}`"
        :class="{ 
          'memorize-button': true,
          'primary': true, 
          'incorrect': wordObj.incorrect 
        }"
        :disabled="wordObj.used"
        @click="selectWord(buttonIndex, wordObj)"
      >
        {{ wordObj.word }}{{ wordObj.remainingUses > 1 ? ` (${wordObj.remainingUses})` : '' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
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
    currentWordIndex: number;
    scrambledWords: WordObject[];
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
    // Split by spaces, but keep punctuation with words
    return text.split(/\s+/).filter(word => word.length > 0);
}

function isWordRevealed(globalWordIndex: number) {
    return globalWordIndex < currentWordIndex.value;
}

onMounted(() => {
    if (props.modeConfig) {
        scrambledWords.value = props.modeConfig.scrambledWords;
        currentWordIndex.value = props.modeConfig.currentWordIndex;
    } else {
        resetWords();
    }
});

function selectWord(buttonIndex: number, wordObj: WordObject) {
    // Check if this is the correct next word
    if (wordObj.originalIndices.includes(currentWordIndex.value)) {
        // Correct word selected
        scrambledWords.value[buttonIndex].remainingUses--;
        scrambledWords.value[buttonIndex].incorrect = false;
        if (scrambledWords.value[buttonIndex].remainingUses === 0) {
            scrambledWords.value[buttonIndex].used = true;
        }
        currentWordIndex.value++;
        
        // Save state after successful word selection
        emit('save-mode-config', {
            currentWordIndex: currentWordIndex.value,
            scrambledWords: scrambledWords.value
        });
    } else {
        // Incorrect word selected
        scrambledWords.value[buttonIndex].incorrect = true;

        // Reset the incorrect status after a short delay
        setTimeout(() => {
            scrambledWords.value[buttonIndex].incorrect = false;
        }, 1000);
    }
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
    wordMap.forEach((data, normalizedWord) => {
        // Find a representative word from the original text (preserve casing/punctuation)
        let originalWord = "";
        const firstIndex = data.indices[0];
        const { itemIndex, localIndex } = getLocalIndices(firstIndex);
        originalWord = getWordsFromText(props.textItems[itemIndex].text)[localIndex];
        
        wordObjects.push({
            word: originalWord,
            originalIndices: data.indices,
            remainingUses: data.count,
            used: false,
            incorrect: false
        });
    });

    // Shuffle the words
    const scrambled = [...wordObjects].sort(() => Math.random() - 0.5);

    // Reset state
    scrambledWords.value = scrambled;
    currentWordIndex.value = 0;
    isPeeking.value = false;

    // Save the initial state
    emit('save-mode-config', {
        currentWordIndex: currentWordIndex.value,
        scrambledWords: scrambledWords.value
    });
}

</script>

<style scoped lang="scss">
@import "~@/common.scss";

.preview {
  background-color: rgba(255, 255, 0, 0.15) !important;
  border: 1px dashed var(--primary-color, #3498db);
}

.text-block {
  margin-bottom: 1rem;
}

.word-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  
  .memorize-button {
    &.incorrect {
      background-color: #e74c3c;
      animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
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
</style>