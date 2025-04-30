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
  <!-- Mode selection -->
  <div class="memorize-mode-selector">
    <div class="button"
        v-for="mode in memorizeModes"
        :key="mode.value"
        :class="{toggled: selectedMode === mode.value}"
        @click="selectedMode = mode.value"
    >
      {{ mode.label }}
    </div>
  </div>
  <h2>{{document.title}}</h2>
  <!-- Different memorize components based on selected mode -->
  <component 
      :is="currentModeComponent"
      :text-items="document.texts"
      :mode-config="document.state?.modeConfig"
      @save-mode-config="saveModeConfig"
  ></component>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {ref, computed, watch, toRefs} from "vue";
import {
    MemorizeDocument,
    MemorizeDocumentState,
    MemorizeModeConfig,
    MemorizeStateMode,
    MemorizeStateModeEnum
} from "@/types/documents";
import WordBlur from '@/components/memorize/WordBlur.vue';
import WordScramble from '@/components/memorize/WordScramble.vue';

const props = defineProps<{ document: MemorizeDocument }>();

const {document} = toRefs(props);

const selectedMode = ref<MemorizeStateMode>(MemorizeStateModeEnum.BLUR);
const modeConfig = ref<MemorizeModeConfig|undefined>(undefined);


if (document.value.state?.mode) {
    selectedMode.value = document.value.state.mode;
}
modeConfig.value = document.value.state?.modeConfig;

const state = computed<MemorizeDocumentState>(() => {
    return {
        mode: selectedMode.value,
        modeConfig: modeConfig.value,
    }
})

const {strings, android} = useCommon();


const memorizeModes = [
    { value: MemorizeStateModeEnum.BLUR, label: strings.wordBlur, component: WordBlur },
    { value: MemorizeStateModeEnum.SCRAMBLE, label: strings.wordScramble, component: WordScramble }
];

function saveModeConfig(_modeConfig: MemorizeModeConfig) {
    modeConfig.value = _modeConfig;
    saveState()
}

watch(selectedMode, saveState);

function saveState() {
    android.saveState(state.value);
}

const currentModeComponent = computed(() => {
    const mode = memorizeModes.find(mode => mode.value === selectedMode.value);
    return mode ? mode.component : WordBlur;
});
</script>

<style scoped lang="scss">
@import "~@/common.scss";
</style>
