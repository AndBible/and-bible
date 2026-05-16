<!--
  - Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div class="osis-markdown" v-html="renderedHtml" @click="handleClick"/>
  <!-- Hidden slot to capture raw content -->
  <span ref="slotContent" style="display: none"><slot/></span>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {Marked} from "marked";
import DOMPurify from "dompurify";
import {useSlotHtmlContent, unescapeXmlEntities, PURIFY_CONFIG} from "@/composables/slot-html-content";

const markdownParser = new Marked({breaks: true, gfm: true});

const {slotContent, rawContent, handleClick} = useSlotHtmlContent();

const renderedHtml = computed(() => {
    if (!rawContent.value) return "";
    const unescaped = unescapeXmlEntities(rawContent.value);
    return DOMPurify.sanitize(markdownParser.parse(unescaped) as string, PURIFY_CONFIG);
});
</script>

<style scoped lang="scss">
@use "@/lib/markdown-render" as md;

.osis-markdown :deep() {
    @include md.markdown-content;
}

.night .osis-markdown :deep() {
    @include md.markdown-content-night;
}

.monochrome .osis-markdown :deep() {
    @include md.markdown-content-monochrome;
}

.monochrome.night .osis-markdown :deep() {
    @include md.markdown-content-monochrome-night;
}
</style>
