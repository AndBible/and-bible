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
  <div
      class="tab-navigation-wrapper"
      :class="{
        'can-scroll-left': canScrollLeft,
        'can-scroll-right': canScrollRight
      }"
  >
    <div ref="navRef" class="tab-navigation" :class="navigationClass">
      <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="tab-button"
          :class="{
            active: activeTab === tab.id,
            disabled: tab.disabled
          }"
          :disabled="tab.disabled"
          @click="handleTabClick(tab.id)"
          :aria-selected="activeTab === tab.id"
          :aria-controls="`tabpanel-${tab.id}`"
          role="tab"
      >
        <FontAwesomeIcon
            v-if="tab.icon"
            :icon="tab.icon"
            class="tab-icon"
        />
        <span class="tab-label">{{ tab.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome';
import type {Tab} from './TabContainer.vue';
import {useScrollOverflow} from '@/composables/scroll-overflow';

const navRef = ref<HTMLElement | null>(null);
const {canScrollLeft, canScrollRight} = useScrollOverflow(navRef);

const props = defineProps<{
  tabs: Tab[];
  activeTab: string;
  navigationClass?: string;
}>();

const emit = defineEmits<{
  'tab-change': [tabId: string];
}>();

function handleTabClick(tabId: string) {
  if (tabId !== props.activeTab) {
    const tab = props.tabs.find(t => t.id === tabId);
    if (tab && !tab.disabled) {
      emit('tab-change', tabId);
    }
  }
}
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.tab-navigation-wrapper {
  position: relative;
  overflow: hidden;

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    width: 24px;
    z-index: 1;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.2s ease;

    .noAnimation & {
      transition: none;
    }
  }

  &::before {
    left: 0;
    background: linear-gradient(to right, var(--background-color), transparent);
  }

  &::after {
    right: 0;
    background: linear-gradient(to left, var(--background-color), transparent);
  }

  &.can-scroll-left::before {
    opacity: 1;
  }

  &.can-scroll-right::after {
    opacity: 1;
  }
}

.tab-navigation {
  display: flex;
  overflow-x: auto;
  &::-webkit-scrollbar { display: none; }
  border-bottom: 2px solid #eee;

  .monochrome & {
    border-bottom-color: black;
  }
  .night & {
    border-bottom-color: #444;
  }
  .monochrome.night & {
    border-bottom-color: white;
  }
}

.tab-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
  .noAnimation & {
    transition: none;
  }
  flex-shrink: 0;

  .monochrome & {
    color: black;
  }
  .night & {
    color: #999;
  }
  .monochrome.night & {
    color: white;
  }

  &:hover:not(:disabled) {
    color: #007bff;
    background: #f8f9fa;

    .monochrome & {
      color: black;
      background: transparent;
      font-weight: 700;
    }
    .night & {
      color: #1e90ff;
      background: #333;
    }
    .monochrome.night & {
      color: white;
      background: transparent;
      font-weight: 700;
    }
  }

  &.active {
    color: #007bff;
    border-bottom-color: #007bff;

    .monochrome & {
      color: black;
      border-bottom-color: black;
    }
    .night & {
      color: #1e90ff;
      border-bottom-color: #1e90ff;
    }
    .monochrome.night & {
      color: white;
      border-bottom-color: white;
    }
  }

  &:disabled,
  &.disabled {
    color: #ccc;
    cursor: not-allowed;

    .monochrome & {
      color: black;
      opacity: 0.5;
    }
    .night & {
      color: #555;
    }
    .monochrome.night & {
      color: white;
      opacity: 0.5;
    }

    &:hover {
      background: transparent;
    }
  }
}

.tab-icon {
  font-size: 16px;
}

.tab-label {
  white-space: nowrap;
}
</style>