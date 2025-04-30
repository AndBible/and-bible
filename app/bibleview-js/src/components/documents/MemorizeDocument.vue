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
  <h2>Memorize!</h2>
  
  <WordBlur :textItems="document.texts"></WordBlur>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {inject, ref} from "vue";
import {appSettingsKey, exportModeKey} from "@/types/constants";
import {MemorizeDocument} from "@/types/documents";
import WordBlur from '@/components/memorize/WordBlur.vue';

const props = defineProps<{ document: MemorizeDocument }>();

const exportMode = inject(exportModeKey, ref(false));
const appSettings = inject(appSettingsKey)!;

const {android, sprintf, strings} = useCommon();
</script>

<style scoped lang="scss">
@import "~@/common.scss";
</style>
