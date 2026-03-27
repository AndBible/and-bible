<!--
  - Copyright (c) 2021-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
      <div class="icon-button" :class="{ disabled: blurLevel === 0 }"
           @pointerdown.prevent="blurLevel > 0 && revealWords(true, false)"
           @pointerup="revealWords(false, false)"
           @pointerleave="revealWords(false, false)"
      >
        <FontAwesomeIcon :icon="faEye"/>
      </div>
      <div class="icon-button" :class="{ disabled: blurLevel <= 1 }"
           @pointerdown.prevent="blurLevel > 1 && revealWords(true, true)"
           @pointerup="revealWords(false, true)"
           @pointerleave="revealWords(false, true)">
        <FontAwesomeIcon :icon="faBackwardStep"/>
      </div>
      <div class="icon-button" @click="increaseBlurLevel">
        <FontAwesomeIcon :icon="faPlus"/><span v-if="blurLevel > 0" class="icon-badge">{{ blurLevel }}</span>
      </div>
      <div class="icon-button" @click="resetBlur">
        <FontAwesomeIcon :icon="faUndo"/>
      </div>
    </div>
    <div class="memorize-text">
      <div v-for="item in textItems" :key="item.key">
        <span
            v-for="(word, wordIndex) in getWordsFromText(item.text)"
            :key="`${item.key}-${wordIndex}`"
            class="memorize-word"
            :class="{
              blurred: isWordBlurred(wordIndex),
              revealed: isWordBlurred(wordIndex) && revealedWords[`${item.key}-${wordIndex}`]
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
import { ref, watch, onMounted } from "vue";
import { MemorizeTextItem } from "@/types/documents";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faBackwardStep, faEye, faPlus, faUndo} from "@fortawesome/free-solid-svg-icons";

interface WordBlurConfig {
    blurConfig: {
        blurLevel: number;
        revealedWords: Record<string, boolean>;
    }
}

const props = defineProps<{
    textItems: MemorizeTextItem[]
    modeConfig: WordBlurConfig | undefined
}>();

const emit = defineEmits<{
    (e: 'save-mode-config', config: WordBlurConfig): void
}>();


const blurLevel = ref(0);
const revealedWords = ref<Record<string, boolean>>({});
const wordRevealTimer = ref<Record<string, number>>({});

onMounted(() => {
    const config = props.modeConfig?.blurConfig;
    if (config) {
        blurLevel.value = config.blurLevel;
        revealedWords.value = config.revealedWords;
    }
});


watch([blurLevel, revealedWords], () => {
    emit('save-mode-config', {
        blurConfig: {
            blurLevel: blurLevel.value,
            revealedWords: revealedWords.value
        }
    });
}, { deep: true });

const getWordsFromText = (text: string) => {
    return text.split(/\s+/).filter(word => word.length > 0);
};

function isWordBlurredAtLevel(wordIndex: number, level: number) {
    if (level <= 0) return false;
    if (level >= 5) return true;

    switch (level) {
        case 1: return wordIndex % 5 === 0;
        case 2: return wordIndex % 5 === 0 || wordIndex % 3 === 0;
        case 3: return wordIndex % 5 === 0 || wordIndex % 3 === 0 || wordIndex % 2 === 0;
        case 4: return (wordIndex % 5 === 0 || wordIndex % 3 === 0 || wordIndex % 2 === 0) ||
                       (wordIndex % 7 !== 0);
        default: return false;
    }
}

function isWordBlurred(wordIndex: number) {
    return isWordBlurredAtLevel(wordIndex, blurLevel.value);
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

/** @param lastOnly - if true, only reveal words added in the current blur level */
function revealWords(show: boolean, lastOnly: boolean) {
    if (show) {
        Object.keys(wordRevealTimer.value).forEach(key => {
            clearTimeout(wordRevealTimer.value[key]);
        });
        wordRevealTimer.value = {};
        const revealed: Record<string, boolean> = {};
        for (const item of props.textItems) {
            const words = getWordsFromText(item.text);
            for (let i = 0; i < words.length; i++) {
                if (isWordBlurred(i) && (!lastOnly || !isWordBlurredAtLevel(i, blurLevel.value - 1))) {
                    revealed[`${item.key}-${i}`] = true;
                }
            }
        }
        revealedWords.value = revealed;
    } else {
        revealedWords.value = {};
    }
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
@use "@/common.scss" as *;

.memorize-text {
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
}

.memorize-word {
  padding: 2px 4px;
  border-radius: 4px;
  border: 1px solid transparent;

  &.blurred {
    background-color: #ccc;
    color: transparent;
    user-select: none;
    -webkit-user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;

    .night & {
      background-color: #555;
    }
    .monochrome & {
      background-color: white;
      border-color: black;
    }
    .monochrome.night & {
      background-color: black;
      border-color: white;
    }

    &.revealed {
      color: inherit;
    }
  }
}


</style>
