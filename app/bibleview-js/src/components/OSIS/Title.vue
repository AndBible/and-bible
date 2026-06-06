<!--
  - Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div class="title-wrapper" v-if="show">
    <h3 ref="titleEl" class="titleStyle" :class="{'skip-offset': isBibleDoc && !isCanonical, isSubTitle}">
      <slot/>
    </h3>
    <button v-if="config.showTitleScrollButton" class="title-scroll-btn" @click.stop="scrollToTitle">↑</button>
  </div>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject, ref} from "vue";
import {bibleDocumentInfoKey, hideTitlesKey} from "@/types/constants";

const props = withDefaults(
    defineProps<{
        type?: string
        subType?: string
        canonical: string
        short: string
    }>(), {
        canonical: "false",
    }
);

const isBibleDoc = inject(bibleDocumentInfoKey) != undefined

checkUnsupportedProps(props, "type", ["sub", "x-gen", "x-psalm-book", "main", "chapter", "section"]);
checkUnsupportedProps(props, "subType", ["x-Chapter", "x-preverse"]);
checkUnsupportedProps(props, "canonical", ["true", "false"]);
const {config, appSettings, calculatedConfig} = useCommon();
const hideTitles = inject(hideTitlesKey, false);

const isCanonical = computed(() => props.canonical === "true");

const show = computed(() =>
    !hideTitles && config.showSectionTitles
    && ((config.showNonCanonical && !isCanonical.value) || isCanonical)
    && !(props.type === "sub" && props.subType === "x-Chapter")
    && props.type !== "chapter"
    && props.type !== "x-gen",
);

const isSubTitle = computed(() => props.type === "sub");

const titleEl = ref<HTMLElement | null>(null);

function scrollToTitle() {
    if (titleEl.value && calculatedConfig) {
        const rect = titleEl.value.getBoundingClientRect();
        window.scrollTo({
            top: window.scrollY + rect.top - calculatedConfig.value.topOffset,
            behavior: appSettings.disableAnimations ? 'instant' : 'smooth'
        });
    }
}
</script>

<style lang="scss">
.listStyle .titleStyle {
  margin-inline-start: -1em;
}

h3.isSubTitle {
  font-size: 110%;
  margin-inline-start: 1em;
}

.title-wrapper {
  position: relative;
}

.title-scroll-btn {
  position: absolute;
  inset-inline-end: 0;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  opacity: 0.3;
  font-size: 120%;
  cursor: pointer;
  padding: 8px 12px;
  line-height: 1;
  color: inherit;
  .monochrome & {
    opacity: 1;
  }
}
</style>
