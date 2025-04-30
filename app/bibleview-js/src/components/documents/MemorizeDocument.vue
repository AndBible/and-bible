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
  
  <!-- Mode selection -->
  <div class="memorize-mode-selector">
    <button 
        v-for="mode in memorizeModes"
        :key="mode.value"
        :class="['mode-button', { active: selectedMode === mode.value }]"
        @click="selectedMode = mode.value"
    >
      {{ mode.label }}
    </button>
  </div>
  
  <!-- Different memorize components based on selected mode -->
  <component 
      :is="currentModeComponent"
      :text-items="document.texts"
  ></component>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {ref, computed} from "vue";
import {MemorizeDocument} from "@/types/documents";
import WordBlur from '@/components/memorize/WordBlur.vue';
import WordScramble from '@/components/memorize/WordScramble.vue';

defineProps<{ document: MemorizeDocument }>();

const {strings} = useCommon();

const BLUR_MODE = 'blur';
const SCRAMBLE_MODE = 'scramble';

const memorizeModes = [
    { value: BLUR_MODE, label: strings.wordBlur, component: WordBlur },
    { value: SCRAMBLE_MODE, label: strings.wordScramble, component: WordScramble }
];

const selectedMode = ref(BLUR_MODE);

const currentModeComponent = computed(() => {
    const mode = memorizeModes.find(mode => mode.value === selectedMode.value);
    return mode ? mode.component : WordBlur;
});
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.memorize-mode-selector {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  overflow-x: auto;
  padding-bottom: 0.5rem;
  
  .mode-button {
    padding: 0.6rem 1rem;
    border-radius: 20px;
    background-color: transparent;
    color: var(--primary-color, #3498db);
    border: 1px solid var(--primary-color, #3498db);
    font-weight: bold;
    cursor: pointer;
    white-space: nowrap;
    
    &.active {
      background-color: var(--primary-color, #3498db);
      color: white;
    }
  }
}
</style>
