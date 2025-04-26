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
  <div v-for="text in document.texts" :key="text.key">
    <span>{{text.key}}</span> <span>{{text.text}}</span>
  </div>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {inject, ref} from "vue";
import {appSettingsKey, exportModeKey} from "@/types/constants";
import {MemorizeDocument} from "@/types/documents";

const props = defineProps<{ document: MemorizeDocument }>();

// eslint-disable-next-line vue/no-setup-props-destructure
const exportMode = inject(exportModeKey, ref(false));
const appSettings = inject(appSettingsKey)!;

const {android, sprintf, strings} = useCommon();
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.ref-link {
  padding-bottom: 0.5em;
  font-weight: bold;
}

</style>
