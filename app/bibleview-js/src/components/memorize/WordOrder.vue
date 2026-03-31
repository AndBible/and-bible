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
  <div class="memorize-controls">
    <div class="icon-button"
         @pointerdown.prevent="isPeeking = true"
         @pointerup="isPeeking = false"
         @pointerleave="isPeeking = false"
    >
      <FontAwesomeIcon :icon="faEye"/>
    </div>
    <div @click="resetWords()" class="icon-button">
      <FontAwesomeIcon :icon="faUndo"/>
    </div>
  </div>

  <!-- Peek mode: show correct text -->
  <div v-if="isPeeking" class="memorize-text preview">
    <div v-for="item in textItems" :key="item.key" class="text-block">
      <span class="memorize-word">{{ item.text }}</span>
    </div>
  </div>

  <!-- Order mode: draggable word tiles -->
  <div v-else class="order-area" :class="{ completed: isCompleted }">
    <draggable
        v-model="tiles"
        item-key="origIdx"
        ghost-class="order-drag-ghost"
        chosen-class="order-drag-chosen"
        drag-class="order-drag-active"
        class="order-tiles-container"
        :disabled="isCompleted"
        :delay="0"
        :delay-on-touch-only="false"
        :animation="150"
        @end="onDragEnd"
    >
      <template #item="{element, index}">
        <div
            class="button small order-tile"
            :class="{
              correct: !isCompleted && isCorrectAt(element, index),
              'completed-tile': isCompleted,
            }"
        >
          {{ element.word }}
        </div>
      </template>
    </draggable>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, computed, watch} from "vue";
import {MemorizeTextItem} from "@/types/documents";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faEye, faUndo} from "@fortawesome/free-solid-svg-icons";
import draggable from "vuedraggable";

interface TileItem {
    origIdx: number;
    word: string;
}

interface WordOrderConfig {
    orderConfig?: {
        currentOrder: number[];
    }
}

const props = defineProps<{
    textItems: MemorizeTextItem[],
    modeConfig: WordOrderConfig | undefined
}>();

const emit = defineEmits<{
    (e: 'save-mode-config', config: WordOrderConfig): void;
    (e: 'memorize-completed'): void;
}>();

const words = ref<string[]>([]);
const tiles = ref<TileItem[]>([]);
const isPeeking = ref<boolean>(false);

function isCorrectAt(tile: TileItem, pos: number): boolean {
    return tile.word.toLowerCase() === words.value[pos].toLowerCase();
}

const isCompleted = computed(() => {
    if (tiles.value.length === 0) return false;
    return tiles.value.every((tile, pos) => isCorrectAt(tile, pos));
});

watch(isCompleted, (completed) => {
    if (completed) emit('memorize-completed');
});

// Character class shared by getWordsFromText and isPunctuation
const PUNCT = String.raw`"".,;:!?…"'«»„‚–—\-()\[\]{}`;
const WORD_TOKENIZER = new RegExp(`([${PUNCT}]+)|([^\\s${PUNCT}]+)`, 'g');
const PUNCTUATION_ONLY = new RegExp(`^[${PUNCT}]+$`);

function getWordsFromText(text: string) {
    const tokens = text.match(WORD_TOKENIZER) || [];
    return tokens.filter(token => token.length > 0);
}

function isPunctuation(word: string): boolean {
    return PUNCTUATION_ONLY.test(word);
}

function buildWordList() {
    const result: string[] = [];
    for (const item of props.textItems) {
        const tokens = getWordsFromText(item.text);
        for (const token of tokens) {
            if (!isPunctuation(token)) {
                result.push(token);
            }
        }
    }
    words.value = result;
}

function onDragEnd() {
    saveState();
}

function getCurrentOrder(): number[] {
    return tiles.value.map(t => t.origIdx);
}

function buildTilesFromOrder(order: number[]): TileItem[] {
    return order.map(origIdx => ({origIdx, word: words.value[origIdx]}));
}

function shuffle(arr: number[]): number[] {
    const result = [...arr];
    for (let i = result.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [result[i], result[j]] = [result[j], result[i]];
    }
    // Ensure it's not already solved
    if (result.every((v, i) => v === i) && result.length > 1) {
        [result[0], result[1]] = [result[1], result[0]];
    }
    return result;
}

function saveState() {
    emit('save-mode-config', {
        orderConfig: {
            currentOrder: getCurrentOrder(),
        }
    });
}

function resetWords() {
    buildWordList();
    const indices = words.value.map((_, i) => i);
    tiles.value = buildTilesFromOrder(shuffle(indices));
    isPeeking.value = false;
    saveState();
}

onMounted(() => {
    buildWordList();
    const config = props.modeConfig?.orderConfig;
    if (config && config.currentOrder?.length === words.value.length) {
        tiles.value = buildTilesFromOrder(config.currentOrder);
    } else {
        resetWords();
    }
});
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.memorize-text {
  &.preview {
    border: 1px dashed var(--primary-color);
    background-color: rgba(0, 0, 0, 0.03);
    .monochrome & {
      background-color: transparent;
      border-color: black;
    }
    .night & {
      background-color: rgba(255, 255, 255, 0.03);
    }
    .monochrome.night & {
      background-color: transparent;
      border-color: white;
    }
    padding: 1rem;
  }

  .memorize-word {
    margin-right: 4px;
    user-select: none;
    -webkit-user-select: none;
  }
}

.text-block {
  margin-bottom: 1rem;
}

.order-area {
  margin-bottom: 1.5rem;
  padding: 1rem;
  border: 2px solid transparent;
  border-radius: 8px;
  transition: border-color 0.3s ease, background-color 0.3s ease;
  .noAnimation & {
    transition: none;
  }

  &.completed {
    border-color: #28a745;
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

.order-tiles-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  touch-action: pan-y !important;
}

.order-tile {
  margin: 2px;
  min-width: auto;
  padding: 8px 12px;
  border-radius: $button-border-radius;
  font-weight: 500;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  cursor: grab;
  transition: background-color 0.2s ease, border-color 0.2s ease;
  .noAnimation & {
    transition: none;
  }

  &:active {
    cursor: grabbing;
  }

  &.correct {
    background-color: #2e7d32;
    .monochrome & {
      background-color: transparent;
      text-decoration: underline;
    }
    .night & {
      background-color: #1b5e20;
    }
    .monochrome.night & {
      background-color: transparent;
      text-decoration: underline;
    }
  }

  &.completed-tile {
    background-color: #2e7d32;
    cursor: default;
    .monochrome & {
      background-color: transparent;
    }
    .night & {
      background-color: #1b5e20;
    }
    .monochrome.night & {
      background-color: transparent;
    }
  }
}

// vuedraggable ghost: the placeholder left behind while dragging
.order-drag-ghost {
  opacity: 0.3;
}

// vuedraggable chosen: the item being dragged (before it detaches)
.order-drag-chosen {
  opacity: 0.85;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  .monochrome & {
    box-shadow: none;
    border: 2px solid black;
  }
  .monochrome.night & {
    border-color: white;
  }
}

// vuedraggable drag: the floating clone following the cursor
.order-drag-active {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  .monochrome & {
    box-shadow: none;
  }
}

@keyframes completionPulse {
  0% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0.4); }
  70% { box-shadow: 0 0 0 10px rgba(40, 167, 69, 0); }
  100% { box-shadow: 0 0 0 0 rgba(40, 167, 69, 0); }
}
</style>
