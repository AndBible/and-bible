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
      <button @click="increaseBlurLevel" class="blur-button">{{ strings.blur }}</button>
      <button @click="resetBlur" class="reset-button">{{strings.reset}}</button>
    </div>
    <div class="memorize-text">
      <div v-for="item in textItems" :key="item.key">
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
import { ref, computed, watch, onMounted } from "vue";
import { useCommon } from "@/composables";
import { MemorizeTextItem } from "@/types/documents";

interface WordBlurConfig {
  blurLevel: number;
  revealedWords: Record<string, boolean>;
}

const props = defineProps<{
    textItems: MemorizeTextItem[]
    modeConfig: WordBlurConfig | undefined
}>();

const emit = defineEmits<{
    (e: 'save-mode-config', config: WordBlurConfig): void
}>();

const { strings } = useCommon();

const blurLevel = ref(0);
const revealedWords = ref<Record<string, boolean>>({});
const wordRevealTimer = ref<Record<string, number>>({});

onMounted(() => {
  if (props.modeConfig) {
    blurLevel.value = props.modeConfig.blurLevel;
    revealedWords.value = props.modeConfig.revealedWords || {};
  }
});

watch([blurLevel, revealedWords], () => {
  saveState();
}, { deep: true });

function saveState() {
  emit('save-mode-config', {
    blurLevel: blurLevel.value,
    revealedWords: revealedWords.value
  });
}

const getWordsFromText = (text: string) => {
    return text.split(/\s+/).filter(word => word.length > 0);
};

function isWordBlurred(wordIndex: number) {
    if (blurLevel.value === 0) return false;
    if (blurLevel.value === 5) return true;

    // Ensure blur is progressive - once a word is blurred at a level,
    // it remains blurred at higher levels
    switch (blurLevel.value) {
        case 1: // ~20% words blurred
            return wordIndex % 5 === 0;
        case 2: // ~40% words blurred
            return wordIndex % 5 === 0 || wordIndex % 3 === 0;
        case 3: // ~60% words blurred
            return wordIndex % 5 === 0 || wordIndex % 3 === 0 || wordIndex % 2 === 0;
        case 4: // ~80% words blurred
            // Only show every 7th word that wasn't already blurred in level 3
            return (wordIndex % 5 === 0 || wordIndex % 3 === 0 || wordIndex % 2 === 0) || 
                   (wordIndex % 7 !== 0);
        default:
            return false;
    }
}

function increaseBlurLevel() {
    if (blurLevel.value < 5) {
        blurLevel.value++;
        revealedWords.value = {};
        Object.keys(wordRevealTimer.value).forEach(key => {
            clearTimeout(wordRevealTimer.value[key]);
        });
        wordRevealTimer.value = {};
    }
}


function resetBlur() {
    blurLevel.value = 0;
    revealedWords.value = {};
    Object.keys(wordRevealTimer.value).forEach(key => {
        clearTimeout(wordRevealTimer.value[key]);
    });
    wordRevealTimer.value = {};
}


function revealWord(textKey: string, wordIndex: number) {
    const key = `${textKey}-${wordIndex}`;
    if (!isWordBlurred(wordIndex)) return;
    if (wordRevealTimer.value[key]) {
        clearTimeout(wordRevealTimer.value[key]);
    }
    revealedWords.value[key] = true;
    wordRevealTimer.value[key] = setTimeout(() => {
        revealedWords.value[key] = false;
    }, 2000) as unknown as number;
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.memorize-controls {
  margin-bottom: 1rem;
  display: flex;
  gap: 0.5rem;
  
  .blur-button {
    @extend .button;
  }

  .reset-button {
    @extend .button;
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

.word {
  position: relative;
  display: inline-block;
  margin-right: 0.25em;
}

.blurred {
  color: transparent;
  background-color: rgba(0, 0, 0, 0.2);
  .night & {
    background-color: rgba(255, 255, 255, 0.2);
  }
  border-radius: 3px;
  padding: 0 2px;
  cursor: pointer;
}

.revealed {
  color: var(--text-color, inherit);
  background-color: rgba(255, 255, 0, 0.3);
  .night & {
    background-color: rgba(0, 0, 0, 0.3);
  }
}
</style>