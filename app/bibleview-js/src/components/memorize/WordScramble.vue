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
    <div v-for="(item, itemIndex) in textItems" :key="item.key" class="scramble-container">
      <span class="reference">{{ item.key }}</span>

      <!-- Text area with revealed words or full preview -->
      <div class="verse-text" :class="{ 'preview': isShowingFullText(itemIndex) || isPeeking[itemIndex] }">
        <template v-if="isShowingFullText(itemIndex) || isPeeking[itemIndex]">
          <span class="word">{{ item.text }}</span>
        </template>
        <template v-else>
          <template v-for="(word, wordIndex) in getWordsFromText(item.text)" :key="`text-${item.key}-${wordIndex}`">
            <span 
              class="word" 
              :class="{ 'revealed': isWordRevealed(itemIndex, wordIndex) }"
            >
              {{ isWordRevealed(itemIndex, wordIndex) ? word : '___' }}
            </span>
          </template>
        </template>
      </div>
      
      <!-- Word buttons in scrambled order -->
      <div class="word-buttons" v-if="!isShowingFullText(itemIndex)">
        <button 
          v-for="(wordObj, buttonIndex) in scrambledWords[itemIndex]" 
          :key="`button-${item.key}-${buttonIndex}`"
          :class="{ 
            'word-button': true, 
            'used': wordObj.used,
            'incorrect': wordObj.incorrect 
          }"
          :disabled="wordObj.used"
          @click="selectWord(itemIndex, buttonIndex, wordObj)"
        >
          {{ wordObj.word }}
        </button>
      </div>
      
      <div class="button-container">
        <!-- Show the peek button only when the game is active (not in preview) -->
        <button 
          v-if="!isShowingFullText(itemIndex)" 
          @touchstart="isPeeking[itemIndex] = true"
          @touchend="isPeeking[itemIndex] = false"
          class="peek-button"
        >
          {{ strings.peek }}
        </button>
        <button @click="resetScramble(itemIndex)" class="reset-button">{{ strings.reset }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useCommon } from "@/composables";
import {sleep} from "@/utils";

interface TextItem {
  key: string;
  text: string;
}

interface WordObject {
  word: string;
  originalIndex: number;
  used: boolean;
  incorrect: boolean;
}

const props = defineProps<{ 
  textItems: TextItem[]
}>();

const { strings } = useCommon();

const scrambledWords = ref<WordObject[][]>([]);
const currentWordIndices = ref<number[]>([]);
const showFullTextTimers = ref<number[]>([]);
const isInitialPreview = ref<boolean[]>([]);
const isPeeking = ref<boolean[]>([]);

function getWordsFromText(text: string) {
    // Split by spaces, but keep punctuation with words
    return text.split(/\s+/).filter(word => word.length > 0);
}


function isWordRevealed(itemIndex: number, wordIndex: number) {
    return wordIndex < currentWordIndices.value[itemIndex];
}

function isShowingFullText(itemIndex: number) {
    return isInitialPreview.value[itemIndex];
}

onMounted(() => {
    // Initialize the scrambled words for each text item
    isInitialPreview.value = Array(props.textItems.length).fill(true);
    isPeeking.value = Array(props.textItems.length).fill(false);
    showFullTextTimers.value = Array(props.textItems.length).fill(0);
  
    for (let i = 0; i < props.textItems.length; i++) {
        initializeWords(i)
    }
});

function selectWord(itemIndex: number, buttonIndex: number, wordObj: WordObject) {
    const currentIndex = currentWordIndices.value[itemIndex];
    const words = getWordsFromText(props.textItems[itemIndex].text);

    // Check if this is the correct next word
    if (wordObj.originalIndex === currentIndex) {
        // Correct word selected
        scrambledWords.value[itemIndex][buttonIndex].used = true;
        scrambledWords.value[itemIndex][buttonIndex].incorrect = false;
        currentWordIndices.value[itemIndex]++;
    } else {
        // Incorrect word selected
        scrambledWords.value[itemIndex][buttonIndex].incorrect = true;

        // Reset the incorrect status after a short delay
        setTimeout(() => {
            scrambledWords.value[itemIndex][buttonIndex].incorrect = false;
        }, 1000);
    }
}

function resetScramble(itemIndex: number) {
    // Clear any active timers
    if (showFullTextTimers.value[itemIndex]) {
        clearTimeout(showFullTextTimers.value[itemIndex]);
    }

    // Start with full text preview again
    isInitialPreview.value[itemIndex] = true;
    initializeWords(itemIndex)
}

function initializeWords(itemIndex: number) {
    const words = getWordsFromText(props.textItems[itemIndex].text);

    // Create new word objects
    const wordObjects = words.map((word, idx) => ({
        word,
        originalIndex: idx,
        used: false,
        incorrect: false
    }));

    // Shuffle the words again
    const scrambled = [...wordObjects].sort(() => Math.random() - 0.5);

    // Reset state
    scrambledWords.value[itemIndex] = scrambled;
    currentWordIndices.value[itemIndex] = 0;

    // Show full text for 3 seconds then hide
    showFullTextTimers.value[itemIndex] = setTimeout(() => {
        isInitialPreview.value[itemIndex] = false;
    }, 3000) as unknown as number;
}

</script>

<style scoped lang="scss">
@import "~@/common.scss";

.scramble-container {
  margin-bottom: 2rem;
}

.reference {
  display: block;
  padding-bottom: 0.5em;
  font-weight: bold;
}

.verse-text {
  line-height: 1.8;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  min-height: 3rem;
  
  &.preview {
    background-color: rgba(255, 255, 0, 0.15);
    border: 1px dashed var(--primary-color, #3498db);
  }
}

.word {
  position: relative;
  display: inline-block;
  margin-right: 0.25em;
}

.revealed {
  color: var(--text-color, inherit);
}

.word-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.word-button {
  padding: 0.5rem 0.8rem;
  border-radius: 4px;
  background-color: var(--primary-color, #3498db);
  color: white;
  border: none;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s ease;
  
  &:disabled {
    opacity: 0.3;
    cursor: not-allowed;
  }
  
  &.used {
    opacity: 0.3;
    pointer-events: none;
  }
  
  &.incorrect {
    background-color: #e74c3c;
    animation: shake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
  }
}

.button-container {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
  
  .reset-button, .peek-button {
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-weight: bold;
    cursor: pointer;
  }
  
  .peek-button {
    background-color: rgba(0, 0, 0, 0.1);
    border: 1px solid rgba(0, 0, 0, 0.2);
    color: var(--text-color, inherit);
    touch-action: manipulation;
  }
  
  .reset-button {
    background-color: transparent;
    border: 1px solid var(--primary-color, #3498db);
    color: var(--primary-color, #3498db);
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