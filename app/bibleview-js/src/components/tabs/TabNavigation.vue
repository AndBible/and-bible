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
  <div class="tab-navigation" :class="navigationClass">
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
</template>

<script setup lang="ts">
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome';
import type {Tab} from './TabContainer.vue';

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

.tab-navigation {
  display: flex;
  border-bottom: 2px solid #eee;
  
  .night & {
    border-bottom-color: #444;
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
  flex: 1;
  justify-content: center;
  
  .night & {
    color: #999;
  }
  
  &:hover:not(:disabled) {
    color: #007bff;
    background: #f8f9fa;
    
    .night & {
      color: #1e90ff;
      background: #333;
    }
  }
  
  &.active {
    color: #007bff;
    border-bottom-color: #007bff;
    
    .night & {
      color: #1e90ff;
      border-bottom-color: #1e90ff;
    }
  }
  
  &:disabled,
  &.disabled {
    color: #ccc;
    cursor: not-allowed;
    
    .night & {
      color: #555;
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