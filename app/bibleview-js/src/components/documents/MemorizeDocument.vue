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
          @memorize-completed="onMemorizeCompleted"
      />
    </template>

    <!-- Word Type Tab -->
    <template #type>
      <WordType
          :text-items="document.texts"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
          @memorize-completed="onMemorizeCompleted"
      />
    </template>
  </TabContainer>

  <!-- Mark as memorized / unmark button -->
  <div class="memorize-actions">
    <div v-if="!isMemorized" class="button" @click="markAsMemorized">
      <FontAwesomeIcon :icon="faCheck"/> {{ strings.markAsMemorized }}
    </div>
    <div v-else class="button memorized" @click="unmarkMemorized">
      <FontAwesomeIcon :icon="faCheck"/> {{ strings.markedAsMemorized }}
    </div>
    <div v-if="isTarget && !isMemorized" class="button target" @click="removeFromTargets">
      <FontAwesomeIcon :icon="faBrain"/> {{ strings.removeFromTargets }}
    </div>
    <div v-if="!isTarget" class="button" @click="addToTargets">
      <FontAwesomeIcon :icon="faBrain"/> {{ strings.addMemorizationTarget }}
    </div>
    <div class="button" @click="openProgress">
      <FontAwesomeIcon :icon="faChartLine"/> {{ strings.viewReadingProgress }}
    </div>
  </div>
</template>

<script lang="ts">
import {MemorizeStateMode} from "@/types/documents";
let lastSelectedMode: MemorizeStateMode | null = null;
</script>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed, ref, toRefs, watch} from "vue";
import {
    MemorizeDocument,
    MemorizeModeConfig,
    MemorizeStateModeEnum, MemorizeState
} from "@/types/documents";
import WordBlur from '@/components/memorize/WordBlur.vue';
import WordScramble from '@/components/memorize/WordScramble.vue';
import WordType from '@/components/memorize/WordType.vue';
import TabContainer from '@/components/tabs/TabContainer.vue';
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faBrain, faChartLine, faCheck, faEyeSlash, faKeyboard, faRandom, faTimes} from "@fortawesome/free-solid-svg-icons";
import {inject} from "vue";
import {memorizationKey} from "@/types/constants";

const props = defineProps<{ document: MemorizeDocument }>();

const {document} = toRefs(props);

const selectedMode = ref<MemorizeStateMode>(document.value.state?.memorize?.mode ?? lastSelectedMode ?? MemorizeStateModeEnum.BLUR);
const modeConfig = ref<MemorizeModeConfig|undefined>(document.value.state?.memorize?.modeConfig);

// Computed for mapping selected mode to tab ID
const selectedTabId = computed(() => {
    switch (selectedMode.value) {
        case MemorizeStateModeEnum.BLUR: return 'blur';
        case MemorizeStateModeEnum.SCRAMBLE: return 'scramble';
        case MemorizeStateModeEnum.TYPE: return 'type';
        default: return 'blur';
    }
});

const memorizeState = computed<MemorizeState>(() => {
    return {
        mode: selectedMode.value,
        modeConfig: modeConfig.value,
    }
})

const {strings, android} = useCommon();
const memorization = inject(memorizationKey)!;

// Populate memorization data so isMemorized/isTarget are reactive
memorization.mergeData(
    document.value.memorizedOrdinals ?? [],
    document.value.targetOrdinals ?? []
);

function withVerseRange(fn: (bookInitials: string, startOrdinal: number, endOrdinal: number) => void) {
    const {bookInitials, startOrdinal, endOrdinal} = document.value;
    if (bookInitials && startOrdinal != null && endOrdinal != null) {
        fn(bookInitials, startOrdinal, endOrdinal);
    }
}

const isMemorized = computed(() => {
    const {startOrdinal, endOrdinal} = document.value;
    if (startOrdinal == null || endOrdinal == null) return false;
    for (let i = startOrdinal; i <= endOrdinal; i++) {
        if (!memorization.memorized.has(i)) return false;
    }
    return true;
});

const isTarget = computed(() => {
    const {startOrdinal, endOrdinal} = document.value;
    if (startOrdinal == null || endOrdinal == null) return false;
    for (let i = startOrdinal; i <= endOrdinal; i++) {
        if (memorization.targets.has(i)) return true;
    }
    return false;
});

function markAsMemorized() {
    withVerseRange((b, s, e) => {
        android.memorizeCompleted(b, s, e);
        if (!isTarget.value) {
            android.addMemorizationTarget(b, s, e);
        }
    });
}

function unmarkMemorized() {
    withVerseRange((b, s, e) => android.unmarkMemorized(b, s, e));
}

function removeFromTargets() {
    withVerseRange((b, s, e) => android.removeMemorizationTarget(b, s, e));
}

function addToTargets() {
    withVerseRange((b, s, e) => android.addMemorizationTarget(b, s, e));
}

function openProgress() {
    android.openReadingProgress(1);
}

// Tab configuration for the TabContainer
const tabsConfig = computed(() => [
    { 
        id: 'blur', 
        label: strings.wordBlur,
        value: MemorizeStateModeEnum.BLUR,
        icon: faEyeSlash,
    },
    {
        id: 'scramble',
        label: strings.wordScramble,
        value: MemorizeStateModeEnum.SCRAMBLE,
        icon: faRandom,
    },
    {
        id: 'type',
        label: strings.wordType,
        value: MemorizeStateModeEnum.TYPE,
        icon: faKeyboard,
    }
]);

// Handle tab/mode change events
function handleModeChange(tabId: string) {
    const modeData = tabsConfig.value.find(config => config.id === tabId);
    if (modeData) {
        selectedMode.value = modeData.value;
        lastSelectedMode = modeData.value;
    }
}

function saveModeConfig(_modeConfig: MemorizeModeConfig) {
    modeConfig.value = {...modeConfig.value, ..._modeConfig};
    saveState()
}

function onMemorizeCompleted() {
    withVerseRange((b, s, e) => android.memorizeCompleted(b, s, e));
}

watch(selectedMode, saveState);

function saveState() {
    android.saveState({
        ...document.value.state,
        memorize: memorizeState.value
    });
}
</script>

<style lang="scss">

.memorize-content {
  margin-top: 0.8em;
}

</style>

<style scoped lang="scss">
@use "@/common.scss" as *;

h2 {
  font-size: 1.2em;
  text-align: center;
}

.memorize-actions {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 1em;

  .button.memorized {
    background-color: #4CAF50;
  }

  .button.target {
    background-color: #9C27B0;
  }
}
</style>
