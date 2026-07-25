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
    <button v-if="config.showTitleScrollButton && outlineCtx?.visible" class="title-outline-btn" @click.stop="outlineCtx.open" :aria-label="strings.outline" :title="strings.outline">≡</button>
    <component :is="headingTag" ref="titleEl" class="titleStyle" :class="{'skip-offset': isBibleDoc && !isCanonical, isSubTitle}" :data-ordinal="ordinal" :data-title-index="titleIndex" tabindex="0" @click="titleClicked" @keydown.enter="titleClicked" @keydown.space.prevent="titleClicked">
      <template v-if="overrideText !== null">{{ overrideText }}</template>
      <slot v-else/>
    </component>
  </div>
</template>

<script setup lang="ts">
import {checkUnsupportedProps, useCommon} from "@/composables";
import {computed, inject, ref} from "vue";
import {bibleDocumentInfoKey, customHeadingsKey, hideTitlesKey, outlineKey} from "@/types/constants";
import {addEventFunction, EventPriorities} from "@/utils";
import {emit} from "@/eventbus";
import {headingOverrideKey, HeadingMenuPayload} from "@/composables/custom-headings";

const props = withDefaults(
    defineProps<{
        type?: string
        subType?: string
        canonical: string
        short: string
        ordinal?: string
        titleIndex?: string
    }>(), {
        canonical: "false",
    }
);

const bibleDocumentInfo = inject(bibleDocumentInfoKey, null);
const customHeadings = inject(customHeadingsKey, null);
const isBibleDoc = bibleDocumentInfo != undefined

checkUnsupportedProps(props, "type", ["sub", "x-gen", "x-psalm-book", "main", "chapter", "section"]);
checkUnsupportedProps(props, "subType", ["x-Chapter", "x-preverse"]);
checkUnsupportedProps(props, "canonical", ["true", "false"]);
const {config, strings} = useCommon();
const hideTitles = inject(hideTitlesKey, false);
const outlineCtx = inject(outlineKey, null);

const isCanonical = computed(() => props.canonical === "true");

const headingOverride = computed(() => {
    if (!bibleDocumentInfo || !customHeadings || props.ordinal == null || props.titleIndex == null) return undefined;
    return customHeadings.headingOverrides.get(
        headingOverrideKey(bibleDocumentInfo.bookInitials, parseInt(props.ordinal), parseInt(props.titleIndex)));
});

const level = computed(() => headingOverride.value?.newLevel ?? 3);
const headingTag = computed(() => `h${level.value}`);
const overrideText = computed(() => headingOverride.value?.newText ?? null);

const show = computed(() =>
    !hideTitles && config.showSectionTitles
    && ((config.showNonCanonical && !isCanonical.value) || isCanonical.value)
    && !(props.type === "sub" && props.subType === "x-Chapter")
    && props.type !== "chapter"
    && props.type !== "x-gen"
    && headingOverride.value?.deleted !== true,
);

const isSubTitle = computed(() => props.type === "sub");

const titleEl = ref<HTMLElement | null>(null);

function titleClicked(event: Event) {
    if (!bibleDocumentInfo || props.ordinal == null || props.titleIndex == null) return;
    const {bookInitials, v11n} = bibleDocumentInfo;
    const payload: HeadingMenuPayload = {
        kind: "module",
        bookInitials,
        v11n: v11n!,
        ordinal: parseInt(props.ordinal),
        titleIndex: parseInt(props.titleIndex),
        text: titleEl.value?.textContent?.trim() ?? "",
        level: level.value,
    };
    addEventFunction(event, () => emit("open_heading_menu", payload),
        {priority: EventPriorities.HEADING, title: payload.text});
}
</script>

<style lang="scss">
.listStyle .titleStyle {
  margin-inline-start: -1em;
}

h1.isSubTitle,
h2.isSubTitle,
h3.isSubTitle,
h4.isSubTitle,
h5.isSubTitle,
h6.isSubTitle {
  font-size: 110%;
  margin-inline-start: 1em;
}

.title-wrapper {
  position: relative;
  padding-inline-start: 28px;
}

.title-outline-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  opacity: 0.4;
  font-size: 100%;
  cursor: pointer;
  padding: 6px 8px;
  line-height: 1;
  color: inherit;
  z-index: 1;

  &:hover {
    opacity: 1;
  }

  .monochrome & {
    opacity: 0.7;
  }
}
</style>
