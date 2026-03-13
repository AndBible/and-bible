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
  <span class="milestone" :class="{paragraphBreak: isParagraphBreak}">{{ marker }}<slot/></span>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject} from "vue";
import {hasParagraphBreakKey} from "@/types/constants";

const props = withDefaults(defineProps<{
    subType?: string
    type?: string
    marker: string
    resp?: string
}>(),{
    marker: "",
    resp: ""
});

checkUnsupportedProps(props, "resp");
checkUnsupportedProps(props, "type", ["x-strongsMarkup", "x-PN", "line"]);
checkUnsupportedProps(props, "subType", ["x-PO", "x-PM"]);

// Injected from Verse.vue. If true, the parent verse already handled the paragraph break.
// in reality, the crosswire kjv never needs this, because the only verses that have a paragraph break inside them
// are for the colophons at the ends of epistles according to the cambridge paragraphs
// which the crosswire kjv doesnt use because its goal is to match the 1769 kjv
const parentHandledParagraphBreak = inject(hasParagraphBreakKey, { value: false });
const isParagraphBreak = computed(() => (props.type === "line" || (props.type === "x-p" && !parentHandledParagraphBreak.value));

useCommon();
</script>
