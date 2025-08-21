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
  <h2>{{document.title}}</h2>
  
  <!-- Mode selection using TabContainer -->
  <TabContainer
      :tabs="tabsConfig"
      :default-tab="selectedTabId"
      container-class="memorize-container"
      navigation-class="memorize-mode-selector"
      content-class="memorize-content"
      :show-navigation="true"
      @tab-change="handleModeChange"
  >
    <!-- Word Blur Tab -->
    <template #blur>
      <WordBlur
          :text-items="document.texts"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
      />
    </template>

    <!-- Word Scramble Tab -->
    <template #scramble>
      <WordScramble
          :text-items="document.texts"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
      />
    </template>
  </TabContainer>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {ref, computed, watch, toRefs} from "vue";
import {
    MemorizeDocument,
    MemorizeModeConfig,
    MemorizeStateMode,
    MemorizeStateModeEnum, MemorizeState
} from "@/types/documents";
import WordBlur from '@/components/memorize/WordBlur.vue';
import WordScramble from '@/components/memorize/WordScramble.vue';
import {TabContainer} from '@/components/tabs';

const props = defineProps<{ document: MemorizeDocument }>();

const {document} = toRefs(props);

const selectedMode = ref<MemorizeStateMode>(document.value.state?.memorize?.mode ?? MemorizeStateModeEnum.BLUR);
const modeConfig = ref<MemorizeModeConfig|undefined>(document.value.state?.memorize?.modeConfig);

// Computed for mapping selected mode to tab ID
const selectedTabId = computed(() => {
    return selectedMode.value === MemorizeStateModeEnum.BLUR ? 'blur' : 'scramble';
});

const memorizeState = computed<MemorizeState>(() => {
    return {
        mode: selectedMode.value,
        modeConfig: modeConfig.value,
    }
})

const {strings, android} = useCommon();

// Tab configuration for the TabContainer
const tabsConfig = computed(() => [
    { 
        id: 'blur', 
        label: strings.wordBlur,
        value: MemorizeStateModeEnum.BLUR
    },
    { 
        id: 'scramble', 
        label: strings.wordScramble,
        value: MemorizeStateModeEnum.SCRAMBLE
    }
]);

// Handle tab/mode change events
function handleModeChange(tabId: string) {
    const modeData = tabsConfig.value.find(config => config.id === tabId);
    if (modeData) {
        selectedMode.value = modeData.value;
    }
}

function saveModeConfig(_modeConfig: MemorizeModeConfig) {
    modeConfig.value = {...modeConfig.value, ..._modeConfig};
    saveState()
}

watch(selectedMode, saveState);

function saveState() {
    android.saveState({
        ...document.value.state,
        memorize: memorizeState.value
    });
}
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.memorize-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.memorize-content {
  flex: 1;
}

h2 {
  text-align: center;
  margin: 1rem 0 1.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  .night & {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
}

// Custom styling for memorize mode selector using TabContainer
:deep(.memorize-mode-selector) {
  display: flex;
  justify-content: center;
  margin: 0.5rem 0 1.5rem;
  padding: 0.5rem;
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: $button-border-radius;
  border-bottom: none !important; // Remove default tab border
  
  .night & {
    background-color: rgba(255, 255, 255, 0.05);
  }
  
  .tab-button {
    min-width: 120px;
    margin: 0 4px;
    font-weight: 500;
    transition: all 0.2s ease;
    border: 1px solid transparent;
    border-radius: $button-border-radius;
    border-bottom: 1px solid transparent !important; // Override default tab styling
    background: rgba(255, 255, 255, 0.5);
    
    .night & {
      background: rgba(0, 0, 0, 0.3);
    }
    
    .noAnimation & {
      transition: none;
    }
    
    &.active {
      transform: scale(1.05);
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      background: var(--button-background-color);
      border-color: var(--button-border-color);
      
      .night & {
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
      }
    }
    
    &:active {
      transform: translateY(1px);
    }
    
    &:hover:not(.active) {
      background: rgba(255, 255, 255, 0.8);
      
      .night & {
        background: rgba(0, 0, 0, 0.5);
      }
    }
  }
}
</style>
