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
  <span class="milestone" :class="{paragraphBreak, paragraphBreakBefore}">{{ marker }}<slot/></span>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject, onMounted, Ref, watch} from "vue";

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
const paragraphBreak = computed(() => props.type === "line");
// x-p is found in crosswire kjv inside the verse because it is intended to be viewed as verse-per-line.  the eBible kjv uses the cambridge paragraphs
const paragraphBreakBefore = computed(() => props.type === "x-p");

const hasParagraphBreak = inject<Ref<boolean>>('hasParagraphBreak');

if (hasParagraphBreak) {
    onMounted(() => {
        if (paragraphBreakBefore.value) {
            hasParagraphBreak.value = true;
        }
    });

    // In case the type changes dynamically
    watch(paragraphBreakBefore, (newVal) => {
        if (newVal) {
            hasParagraphBreak.value = true;
        }
    });
}

useCommon();
</script>

<style lang="scss">
@use "@/common.scss" as *;

// When a verse contains a paragraph-breaking milestone, we reformat the verse header.
// We target the class added to .highlight-transition in Verse.vue via Provide/Inject.
.highlight-transition.has-paragraph-break {
  display: inline;

  &::before {
    content: "";
    display: block;
    height: 0.5em;
  }

  .paragraphBreakBefore {
    // Ensure the pilcrow stays inline and has a small gap after the verse number
    display: inline;
    padding-inline-start: 0.3em;
  }
}

// Fallback/Standard milestone styling
.paragraphBreak {
  display: block;
  height: 0.5em;
}
</style>
