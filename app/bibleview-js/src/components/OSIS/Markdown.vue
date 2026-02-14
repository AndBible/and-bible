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
  <div class="osis-markdown" v-html="renderedHtml" @click="handleClick" ref="container"/>
  <!-- Hidden slot to capture raw content -->
  <span ref="slotContent" style="display: none"><slot/></span>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from "vue";
import {Marked} from "marked";
import DOMPurify from "dompurify";

const markdownParser = new Marked({breaks: true, gfm: true});

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
    const unescaped = rawContent.value
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&amp;/g, "&");

    // Parse markdown to HTML and sanitize
    return DOMPurify.sanitize(markdownParser.parse(unescaped) as string);
});

/**
 * Handle clicks on links within the rendered markdown.
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
            // sword://, osis://, ab-w:// protocols will be intercepted by Android
            window.location.assign(href);
        }
    }
}
</script>

<style scoped lang="scss">
@use "@/lib/markdown-render" as md;

.osis-markdown :deep() {
    @include md.markdown-content;
}

.night .osis-markdown :deep() {
    @include md.markdown-content-night;
}
</style>
