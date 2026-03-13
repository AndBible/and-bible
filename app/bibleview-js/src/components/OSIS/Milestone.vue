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
  <span class="milestone" :class="{paragraphBreak: isParagraphBreak, paragraphBreakBefore: isParagraphBreakBefore}">{{ marker }}<slot/></span>
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
checkUnsupportedProps(props, "type", ["x-strongsMarkup", "x-PN", "line", "x-p"]);
checkUnsupportedProps(props, "subType", ["x-PO", "x-PM"]);

// Injected from Verse.vue. If true, the parent verse already handled the paragraph break.
const parentHandledParagraphBreak = inject(hasParagraphBreakKey, { value: false });

const isParagraphBreak = computed(() => props.type === "line");

// Only trigger a paragraph break styling on the Milestone itself if the parent Verse hasn't already.
const isParagraphBreakBefore = computed(() => props.type === "x-p" && !parentHandledParagraphBreak.value);

useCommon();
</script>

<style lang="scss">
@use "@/common.scss" as *;

.paragraphBreakBefore {
  // Reuse the common.scss paragraph break logic
  @extend .paragraphBreak;
  
  // Ensure the pilcrow stays inline and has a small gap
  display: inline;
  padding-inline-start: 0.3em;
  
  // The line break effect is achieved by common.scss's .paragraphBreak pseudo-elements or display
}
</style>
