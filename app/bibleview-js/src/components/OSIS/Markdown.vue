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
import {marked} from "marked";

// Configure marked for safe rendering
marked.setOptions({
    breaks: true,  // Convert \n to <br>
    gfm: true,     // GitHub Flavored Markdown
});

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

    // Parse markdown to HTML
    return marked.parse(unescaped) as string;
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
.osis-markdown {
    // Markdown content styling
    :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
        margin-top: 1em;
        margin-bottom: 0.5em;
        font-weight: bold;
    }

    :deep(h1) { font-size: 1.5em; }
    :deep(h2) { font-size: 1.3em; }
    :deep(h3) { font-size: 1.2em; }

    :deep(p) {
        margin: 0.5em 0;
    }

    :deep(ul), :deep(ol) {
        margin: 0.5em 0;
        padding-left: 1.5em;
    }

    :deep(li) {
        margin: 0.25em 0;
    }

    :deep(blockquote) {
        border-left: 3px solid #ccc;
        margin: 0.5em 0;
        padding-left: 1em;
        color: #666;
    }

    :deep(code) {
        background-color: #f4f4f4;
        padding: 0.2em 0.4em;
        border-radius: 3px;
        font-family: monospace;
    }

    :deep(pre) {
        background-color: #f4f4f4;
        padding: 0.5em;
        border-radius: 5px;
        overflow-x: auto;
    }

    :deep(a) {
        color: #1a73e8;
        text-decoration: underline;
    }

    :deep(hr) {
        border: none;
        border-top: 1px solid #ccc;
        margin: 1em 0;
    }

    :deep(table) {
        border-collapse: collapse;
        margin: 0.5em 0;
        width: 100%;
    }

    :deep(th), :deep(td) {
        border: 1px solid #ddd;
        padding: 0.5em;
        text-align: left;
    }

    :deep(th) {
        background-color: #f4f4f4;
    }
}

// Night mode adjustments
.night .osis-markdown {
    :deep(blockquote) {
        border-left-color: #555;
        color: #aaa;
    }

    :deep(code), :deep(pre) {
        background-color: #2d2d2d;
    }

    :deep(a) {
        color: #8ab4f8;
    }

    :deep(th) {
        background-color: #2d2d2d;
    }

    :deep(th), :deep(td) {
        border-color: #444;
    }
}
</style>
