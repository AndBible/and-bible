<!--
  - Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div class="chapter-number ordinal" :data-ordinal="ordinal" v-if="config.showChapterNumbers && startTag && chapterNum !== '0'">{{sprintf(strings.chapterNum, chapterNum)}}</div>
  <slot/>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject} from "vue";
import {bibleDocumentInfoKey} from "@/types/constants";

const props = defineProps<{
    n?: string
    osisID?: string
    sID?: string
    eID?: string
    chapterTitle?: string
}>();

checkUnsupportedProps(props, "chapterTitle")
const bibleDocumentInfo = inject(bibleDocumentInfoKey);
const ordinal = computed(() => {
    if (!bibleDocumentInfo) return -1;
    const ordinalRange = bibleDocumentInfo.originalOrdinalRange || bibleDocumentInfo.ordinalRange;
    return ordinalRange[0];
});
const startTag = computed(() => !props.eID);
const chapterNum = computed(() => {
    return (props.n || props.osisID!.split(".")[1]).trim()
});
const {config, sprintf, strings} = useCommon();

</script>

<style lang="scss">
.chapter-number {
  color: var(--verse-number-color);
  font-size: 70%;
  margin-top: 1em;
  margin-bottom: 0.5em;
  text-align: center;
}
</style>
