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
  <div class="osis-html" v-html="renderedHtml" @click="handleClick"/>
  <!-- Hidden slot to capture raw content -->
  <span ref="slotContent" style="display: none"><slot/></span>
</template>

<script setup lang="ts">
import {computed} from "vue";
import DOMPurify from "dompurify";
import {useSlotHtmlContent, unescapeXmlEntities, PURIFY_CONFIG} from "@/composables/slot-html-content";

const {slotContent, rawContent, handleClick} = useSlotHtmlContent();

const renderedHtml = computed(() => {
    if (!rawContent.value) return "";
    return DOMPurify.sanitize(unescapeXmlEntities(rawContent.value), PURIFY_CONFIG);
});
</script>

<style scoped lang="scss">
.osis-html {
    :deep(a) {
        color: var(--link-color);
        text-decoration: underline;
    }
}

.night .osis-html {
    :deep(a) {
        color: var(--link-color);
    }
}
</style>
