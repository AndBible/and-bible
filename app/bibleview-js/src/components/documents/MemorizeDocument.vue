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
  <div class="memorize-wrapper" :class="{ 'memorized-border': isMemorized }">
  <h2 v-if="!includeReference"><a class="title-link" :href="bibleUrl">{{document.title}}</a></h2>

  <TabContainer
      :tabs="tabsConfig"
      :default-tab="selectedTabId"
      container-class="memorize-container"
      navigation-class="memorize-mode-selector"
      content-class="memorize-content"
      :show-navigation="true"
      @tab-change="handleModeChange"
  >
    <template #trailing>
      <div class="menu-wrapper" ref="menuWrapper">
        <div class="menu-trigger" @click="toggleMenu">
          <FontAwesomeIcon :icon="faEllipsisV"/>
        </div>
        <div v-if="menuOpen" class="dropdown-menu">
          <div v-if="!isMemorized" class="menu-item" @click="menuAction(markAsMemorized)">
            <FontAwesomeIcon :icon="faCheck"/> {{ strings.markAsMemorized }}
          </div>
          <div v-else class="menu-item memorized" @click="menuAction(unmarkMemorized)">
            <FontAwesomeIcon :icon="faCheck"/> {{ strings.markedAsMemorized }}
          </div>
          <div v-if="isTarget && !isMemorized" class="menu-item" @click="menuAction(removeFromTargets)">
            <FontAwesomeIcon :icon="faBrain"/> {{ strings.removeFromTargets }}
          </div>
          <div v-if="!isTarget" class="menu-item" @click="menuAction(addToTargets)">
            <FontAwesomeIcon :icon="faBrain"/> {{ strings.addMemorizationTarget }}
          </div>
          <div class="menu-item" @click="menuAction(openProgress)">
            <FontAwesomeIcon :icon="faChartLine"/> {{ strings.viewReadingProgress }}
          </div>
          <div class="menu-item" @click="menuAction(openSettings)">
            <FontAwesomeIcon :icon="faCog"/> {{ strings.viewReadingProgressSettings }}
          </div>
          <div class="menu-item" @click="menuAction(listenInLoop)">
            <FontAwesomeIcon :icon="faVolumeUp"/> {{ strings.listenInLoop }}
          </div>
          <div class="menu-item" @click="menuAction(openHelp)">
            <FontAwesomeIcon :icon="faQuestionCircle"/> {{ strings.viewHelp }}
          </div>
        </div>
      </div>
    </template>

    <!-- Word Blur Tab -->
    <template #blur>
      <WordBlur
          :text-items="effectiveTextItems"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
      />
    </template>

    <!-- Word Scramble Tab -->
    <template #scramble>
      <WordScramble
          :text-items="effectiveTextItems"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
          @memorize-completed="onMemorizeCompleted"
      />
    </template>

    <!-- Word Type Tab -->
    <template #type>
      <WordType
          :text-items="effectiveTextItems"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
          @memorize-completed="onMemorizeCompleted"
      />
    </template>

    <!-- Word Order Tab -->
    <template #order>
      <WordOrder
          :text-items="effectiveTextItems"
          :mode-config="document.state?.memorize?.modeConfig"
          @save-mode-config="saveModeConfig"
          @memorize-completed="onMemorizeCompleted"
      />
    </template>
  </TabContainer>
  </div>
</template>

<script lang="ts">
import {MemorizeStateMode} from "@/types/documents";
let lastSelectedMode: MemorizeStateMode | null = null;
</script>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed, onBeforeUnmount, onMounted, provide, ref, toRefs, watch} from "vue";
import {
    MemorizeDocument,
    MemorizeModeConfig,
    MemorizeStateModeEnum, MemorizeState,
    MemorizeTextItem
} from "@/types/documents";
import {useReadingProgressSettings} from "@/composables/reading-progress-settings";
import WordBlur from '@/components/memorize/WordBlur.vue';
import WordScramble from '@/components/memorize/WordScramble.vue';
import WordType from '@/components/memorize/WordType.vue';
import WordOrder from '@/components/memorize/WordOrder.vue';
import TabContainer from '@/components/tabs/TabContainer.vue';
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faBrain, faChartLine, faCheck, faCog, faEllipsisV, faEyeSlash, faKeyboard, faQuestionCircle, faRandom, faSort, faTimes, faVolumeUp} from "@fortawesome/free-solid-svg-icons";
import {inject} from "vue";
import {memorizationKey, readingProgressSettingsKey} from "@/types/constants";

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
        case MemorizeStateModeEnum.ORDER: return 'order';
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
const readingProgressSettings = useReadingProgressSettings(document.value.readingProgressSettings, android);
provide(readingProgressSettingsKey, readingProgressSettings);

const includeReference = computed(() => readingProgressSettings.settings.memorizeIncludeReference);

const effectiveTextItems = computed<MemorizeTextItem[]>(() => {
    if (includeReference.value && document.value.title) {
        return [...document.value.texts, {key: '__reference__', text: document.value.title}];
    }
    return document.value.texts;
});

watch(includeReference, () => {
    modeConfig.value = undefined;
    saveState();
});

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
        android.markAsMemorized(b, s, e);
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

function openSettings() {
    android.openReadingProgressSettings();
}

function listenInLoop() {
    const {bookInitials, v11n, startOrdinal, endOrdinal} = document.value;
    if (bookInitials && v11n && startOrdinal != null && endOrdinal != null) {
        android.speakMemorizationLoop(bookInitials, v11n, startOrdinal, endOrdinal);
    }
}

function openHelp() {
    android.showHelpDialog('memorize');
}

const bibleUrl = computed(() => {
    const {osisRef, v11n} = document.value;
    if (osisRef && v11n) {
        return `osis://?osis=${encodeURI(osisRef)}&v11n=${encodeURI(v11n)}`;
    }
    return "#";
});

const menuOpen = ref(false);
const menuWrapper = ref<HTMLElement | null>(null);

function toggleMenu() {
    menuOpen.value = !menuOpen.value;
}

function menuAction(fn: () => void) {
    menuOpen.value = false;
    fn();
}

function onClickOutside(e: Event) {
    if (menuWrapper.value && !menuWrapper.value.contains(e.target as Node)) {
        menuOpen.value = false;
    }
}

onMounted(() => document.value && window.addEventListener('click', onClickOutside));
onBeforeUnmount(() => window.removeEventListener('click', onClickOutside));

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
    },
    {
        id: 'order',
        label: strings.wordOrder,
        value: MemorizeStateModeEnum.ORDER,
        icon: faSort,
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
    if (!readingProgressSettings.settings.autoMarkMemorized) return;
    withVerseRange((b, s, e) => android.markAsMemorized(b, s, e));
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

.memorize-wrapper {
  border: 2px solid transparent;
  border-radius: 8px;
  padding: 4px;
  transition: border-color 0.3s ease;

  .noAnimation & {
    transition: none;
  }

  &.memorized-border {
    border-color: #4CAF50;

    .monochrome & {
      border-color: #666;
    }
    .monochrome.night & {
      border-color: #999;
    }
  }
}

h2 {
  font-size: 1.2em;
  text-align: center;

  .title-link {
    text-decoration: underline;
  }
}

.menu-wrapper {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.menu-trigger {
  cursor: pointer;
  padding: 8px 12px;
  color: #666;
  font-size: 18px;

  .night & {
    color: #999;
  }
  .monochrome & {
    color: black;
  }
  .monochrome.night & {
    color: white;
  }
}

.dropdown-menu {
  position: absolute;
  right: 0;
  top: 100%;
  background: var(--background-color);
  border: 1px solid rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
  min-width: 200px;
  padding: 4px 0;
  animation: dropdown-fade 0.15s ease;

  .night & {
    border-color: rgba(255, 255, 255, 0.3);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  }
  .monochrome & {
    border-color: black;
    box-shadow: none;
  }
  .monochrome.night & {
    border-color: white;
  }
  .noAnimation & {
    animation: none;
  }
}

@keyframes dropdown-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
  color: #666;

  .night & {
    color: #999;
  }
  .monochrome & {
    color: black;
  }
  .monochrome.night & {
    color: white;
  }

  &:hover {
    background: rgba(0, 0, 0, 0.05);
  }
  .night &:hover {
    background: rgba(255, 255, 255, 0.1);
  }
  .monochrome &:hover {
    background: rgba(0, 0, 0, 0.1);
  }
  .monochrome.night &:hover {
    background: rgba(255, 255, 255, 0.15);
  }

  &.memorized {
    color: #4CAF50;
  }
}
</style>
