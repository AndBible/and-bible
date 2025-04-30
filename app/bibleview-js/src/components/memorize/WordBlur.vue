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
      <button @click="increaseBlurLevel" class="blur-button">{{ blurButtonText }}</button>
      <button @click="resetBlur" class="reset-button">Reset</button>
    </div>
    <div v-for="item in textItems" :key="item.key" class="memorize-text">
      <span class="reference">{{ item.key }}</span>
      <div class="verse-text">
        <span 
          v-for="(word, wordIndex) in getWordsFromText(item.text)" 
          :key="`${item.key}-${wordIndex}`"
          :class="{ 
            'word': true, 
            'blurred': isWordBlurred(wordIndex),
            'revealed': revealedWords[`${item.key}-${wordIndex}`]
          }"
          @click="revealWord(item.key, wordIndex)"
        >
          {{ word }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, inject} from "vue";
import {MemorizeTextItem} from "@/types/documents";
import {appSettingsKey, exportModeKey} from "@/types/constants";
import {useCommon} from "@/composables";

defineProps<{textItems: MemorizeTextItem[]}>();

const exportMode = inject(exportModeKey, ref(false));
const appSettings = inject(appSettingsKey)!;

const {android, sprintf, strings} = useCommon();

// Word blur implementation
const blurLevel = ref(0);
const revealedWords = ref<Record<string, boolean>>({});
const wordRevealTimer = ref<Record<string, number>>({});

// Computed property for blur button text
const blurButtonText = computed(() => {
  if (blurLevel.value === 0) {
    return "Start Blurring";
  } else if (blurLevel.value < 5) {
    return "Blur More Words";
  } else {
    return "All Words Blurred";
  }
});

// Function to get words from text
const getWordsFromText = (text: string) => {
  // Split by spaces, but keep punctuation with words
  return text.split(/\s+/).filter(word => word.length > 0);
};

// Function to check if a word should be blurred
const isWordBlurred = (wordIndex: number) => {
  if (blurLevel.value === 0) return false;
  if (blurLevel.value === 5) return true;
  
  // Different blur patterns based on level
  // Level 1: Every 5th word
  if (blurLevel.value === 1) return wordIndex % 5 === 0;
  // Level 2: Every 3rd word
  if (blurLevel.value === 2) return wordIndex % 3 === 0;
  // Level 3: Every 2nd word
  if (blurLevel.value === 3) return wordIndex % 2 === 0;
  // Level 4: Every word except every 5th
  if (blurLevel.value === 4) return wordIndex % 5 !== 0;
  
  return false;
};

// Function to increase blur level
const increaseBlurLevel = () => {
  if (blurLevel.value < 5) {
    blurLevel.value++;
    // Clear any revealed words when changing level
    revealedWords.value = {};
    Object.keys(wordRevealTimer.value).forEach(key => {
      clearTimeout(wordRevealTimer.value[key]);
    });
    wordRevealTimer.value = {};
  }
};

// Function to reset blur
const resetBlur = () => {
  blurLevel.value = 0;
  revealedWords.value = {};
  Object.keys(wordRevealTimer.value).forEach(key => {
    clearTimeout(wordRevealTimer.value[key]);
  });
  wordRevealTimer.value = {};
};

// Function to temporarily reveal a word when tapped
const revealWord = (textKey: string, wordIndex: number) => {
  const key = `${textKey}-${wordIndex}`;
  
  // Only handle clicks on blurred words
  if (!isWordBlurred(wordIndex)) return;
  
  // Clear existing timer if any
  if (wordRevealTimer.value[key]) {
    clearTimeout(wordRevealTimer.value[key]);
  }
  
  // Show the word
  revealedWords.value[key] = true;
  
  // Hide it after 2 seconds
  wordRevealTimer.value[key] = setTimeout(() => {
    revealedWords.value[key] = false;
  }, 2000) as unknown as number;
};
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.memorize-controls {
  margin-bottom: 1rem;
  display: flex;
  gap: 0.5rem;
  
  button {
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-weight: bold;
    cursor: pointer;
  }
  
  .blur-button {
    background-color: var(--primary-color, #3498db);
    color: white;
    border: none;
  }
  
  .reset-button {
    background-color: transparent;
    border: 1px solid var(--primary-color, #3498db);
    color: var(--primary-color, #3498db);
  }
}

.memorize-text {
  margin-bottom: 1rem;
}

.reference {
  display: block;
  padding-bottom: 0.5em;
  font-weight: bold;
}

.verse-text {
  line-height: 1.6;
}

.word {
  position: relative;
  display: inline-block;
  margin-right: 0.25em;
}

.blurred {
  color: transparent;
  background-color: rgba(0, 0, 0, 0.2);
  border-radius: 3px;
  padding: 0 2px;
  cursor: pointer;
}

.revealed {
  color: var(--text-color, inherit);
  background-color: rgba(255, 255, 0, 0.3);
}
</style>