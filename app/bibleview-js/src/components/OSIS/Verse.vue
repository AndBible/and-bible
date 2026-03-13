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
  <span :id="`v-${ordinal}`" @click="verseClicked">
    <span
      :id="fromBibleDocument ? `o-${ordinal}` : undefined"
      class="verse"
      :class="{ordinal: fromBibleDocument}"
      :data-ordinal="ordinal"
    >
      <span v-if="hasParagraphBreak" class="paragraphBreak">&nbsp;</span>
      <span class="highlight-transition" :class="{isHighlighted: highlighted}">
        <VerseNumber v-if="shown && config.showVerseNumbers && verse !== 0" :verse-num="verse"/><slot/> <span/>
      </span>
    </span>
  </span>
  <span :class="{linebreak: config.showVersePerLine}"/>
</template>

<script setup lang="ts">
import {computed, inject, provide, reactive, ref, useSlots} from "vue";
import type { VNode } from "vue";
import VerseNumber from "@/components/VerseNumber.vue";
import {useCommon} from "@/composables";
import {addEventVerseInfo, getVerseInfo} from "@/utils";
import {androidKey, bibleDocumentInfoKey, hasParagraphBreakKey, ordinalHighlightKey, verseInfoKey} from "@/types/constants";
import {VerseInfo} from "@/types/common";
const props = defineProps<{ osisID: string, verseOrdinal: string }>();
const slots = useSlots();

const shown = ref(true);

/**
 * Recursively inspects VNodes to find if a Milestone x-p is the first meaningful content.
 */
function isParagraphStart(nodes: VNode[] | undefined): boolean | null {
    if (!nodes) return null;

    for (const node of nodes) {
        if (typeof node.children === 'string' && node.children.trim() === '') continue;

        const type = node.type as any;
        const typeName = type?.name || type?.__name || (typeof type === 'string' ? type : '');
        if (typeof node.children === 'string' || typeName === 'W') {
            return false;
        }

        if (typeName === 'Milestone' && node.props?.type === 'x-p') {
            return true;
        }

        if (Array.isArray(node.children)) {
            const result = isParagraphStart(node.children as VNode[]);
            if (result !== null) return result;
        }
        
        if (node.children && typeof node.children === 'object' && 'default' in node.children) {
            const slotNodes = (node.children as any).default?.();
            const result = isParagraphStart(slotNodes);
            if (result !== null) return result;
        }
    }
    
    return null;
}

const hasParagraphBreak = computed(() => {
    return isParagraphStart(slots.default?.()) === true;
});

provide(hasParagraphBreakKey, hasParagraphBreak);

const bibleDocumentInfo = inject(bibleDocumentInfoKey);
const {querySelection} = inject(androidKey)!
const {highlightOrdinal, isHighlighted} = inject(ordinalHighlightKey)!;

const verseInfo: VerseInfo = {...getVerseInfo(props), v11n: bibleDocumentInfo?.v11n, showStack: reactive([shown])};
provide(verseInfoKey, verseInfo);

const ordinal = computed(() => {
    return parseInt(props.verseOrdinal);
});

const verse = computed(() => {
    return parseInt(props.osisID.split(".")[2])
});

const fromBibleDocument = computed(() => !!bibleDocumentInfo?.ordinalRange);

const highlighted = computed(() => isHighlighted(ordinal.value));

if (bibleDocumentInfo?.originalOrdinalRange &&
    ordinal.value <= bibleDocumentInfo.originalOrdinalRange[1] &&
    ordinal.value >= bibleDocumentInfo.originalOrdinalRange[0]) {
    highlightOrdinal(ordinal.value)
}

function verseClicked(event: Event) {
    if (!fromBibleDocument.value) return;
    if(querySelection() != null) return;

    const {bookInitials, bibleBookName} = bibleDocumentInfo!;

    addEventVerseInfo(event, {bookInitials, bibleBookName, bibleDocumentInfo, ...verseInfo})
}

const {config} = useCommon();
</script>

<style lang="scss">
@use "@/common.scss" as *;


.linebreak {
  display: block;
  height: 0.3em;
}
</style>
