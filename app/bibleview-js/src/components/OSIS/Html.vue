<!--
  - Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div class="osis-html" v-html="renderedHtml" @click="handleClick" ref="container"/>
  <!-- Hidden slot to capture raw content -->
  <span ref="slotContent" style="display: none"><slot/></span>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from "vue";

const slotContent = ref<HTMLElement | null>(null);
const container = ref<HTMLElement | null>(null);
const rawContent = ref("");

// Get content from slot after mount
onMounted(() => {
    if (slotContent.value) {
        rawContent.value = slotContent.value.innerText;
    }
});

// Watch for changes in slot content
watch(() => slotContent.value?.innerText, (newVal) => {
    if (newVal) {
        rawContent.value = newVal;
    }
});

const renderedHtml = computed(() => {
    if (!rawContent.value) return "";

    // Unescape XML entities that were escaped on the backend
    return rawContent.value
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&amp;/g, "&");
});

/**
 * Handle clicks on links within the rendered HTML.
 * AndBible protocols (sword://, osis://, ab-w://) are handled by the WebView.
 */
function handleClick(event: MouseEvent) {
    const target = event.target as HTMLElement;

    // Check if clicked element is a link or inside a link
    const link = target.closest("a") as HTMLAnchorElement | null;
    if (link) {
        event.preventDefault();
        const href = link.getAttribute("href");
        if (href) {
            // Let the WebView handle the navigation
            window.location.assign(href);
        }
    }
}
</script>

<style scoped lang="scss">
.osis-html {
    // Basic HTML content styling - inherits most from parent
    :deep(a) {
        color: #1a73e8;
        text-decoration: underline;
    }
}

// Night mode adjustments
.night .osis-html {
    :deep(a) {
        color: #8ab4f8;
    }
}
</style>
