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
  <template v-if="isParagraph">
    <span v-if="verseInfo" class="paragraphBreak">&nbsp;</span>
    <div v-else class="paragraphBreak">&nbsp;</div>
  </template>
  <VerseNumber v-else-if="isPreVerse && shown" :verse-num="verseNum"/>
  <template v-else-if="isCanonical || (!isCanonical && config.showNonCanonical)">
    <span v-if="verseInfo" :class="{'skip-offset': !isCanonical}"><slot/></span>
    <div v-else :class="{'skip-offset': !isCanonical}"><slot/></div>
  </template>
</template>

<script setup lang="ts">
import {computed, inject, ref} from "vue";
import VerseNumber from "@/components/VerseNumber.vue";
import {checkUnsupportedProps, useCommon} from "@/composables";
import {verseInfoKey} from "@/types/constants";

const props = defineProps({
    osisID: {type: String, default: null},
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    type: {type: String, default: null},
    subType: {type: String, default: null},
    annotateRef: {type: String, default: null},
    canonical: {type: String, default: null},
    annotateType: {type: String, default: null},
});

checkUnsupportedProps(
    props,
    "type",
    [
        "x-p", "x-gen", "x-milestone", "section", "majorSection", "paragraph", "book",
        "variant", "introduction", "colophon"
    ]);
checkUnsupportedProps(props, "canonical", ["true", "false"]);
checkUnsupportedProps(props, "subType", ["x-preverse"]);
checkUnsupportedProps(props, "annotateRef");
checkUnsupportedProps(props, "annotateType");

const verseInfo = inject(verseInfoKey, null);
let shown = ref(false);

function getIsPreVerse(type: string, subType: string) {
    return type === "x-milestone" && subType === "x-preverse";
}

if (getIsPreVerse(props.type, props.subType) && verseInfo) {
    shown = ref(true);
    for (const oldValue of verseInfo.showStack) {
        oldValue.value = false;
    }
    verseInfo.showStack.push(shown);
}
const {config} = useCommon();
const isParagraph = computed(() => ['x-p', 'paragraph', 'colophon'].includes(props.type) && props.sID);
const isCanonical = computed(() => props.canonical !== "false");
const isPreVerse = computed(() => getIsPreVerse(props.type, props.subType));
const verseNum = computed(() => verseInfo!.verse);
</script>

<style lang="scss" scoped>
@import "~@/common.scss";
</style>
