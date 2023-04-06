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
      <span class="highlight-transition" :class="{isHighlighted: highlighted}">
        <VerseNumber v-if="shown && config.showVerseNumbers && verse !== 0" :verse-num="verse"/><slot/> <span/>
      </span>
    </span>
  </span>
  <span :class="{linebreak: config.showVersePerLine}"/>
</template>

<script setup lang="ts">
import {computed, inject, provide, reactive, ref} from "vue";
import VerseNumber from "@/components/VerseNumber.vue";
import {useCommon} from "@/composables";
import {addEventVerseInfo, getVerseInfo} from "@/utils";
import {bibleDocumentInfoKey, verseHighlightKey, verseInfoKey} from "@/types/constants";
import {VerseInfo} from "@/types/common";

const props = defineProps<{ osisID: string, verseOrdinal: string }>();

const shown = ref(true);
const bibleDocumentInfo = inject(bibleDocumentInfoKey);

const verseInfo: VerseInfo = {...getVerseInfo(props), v11n: bibleDocumentInfo?.v11n, showStack: reactive([shown])};

provide(verseInfoKey, verseInfo);

const {highlightedVerses, highlightVerse} = inject(verseHighlightKey)!;

const ordinal = computed(() => {
    return parseInt(props.verseOrdinal);
});

const verse = computed(() => {
    return parseInt(props.osisID.split(".")[2])
});

const fromBibleDocument = computed(() => !!bibleDocumentInfo?.ordinalRange);

const highlighted = computed(() => highlightedVerses.has(ordinal.value))

if (bibleDocumentInfo?.originalOrdinalRange &&
    ordinal.value <= bibleDocumentInfo.originalOrdinalRange[1] &&
    ordinal.value >= bibleDocumentInfo.originalOrdinalRange[0]) {
    highlightVerse(ordinal.value)
}

function verseClicked(event: Event) {
    if (!fromBibleDocument.value) return;
    const {bookInitials, bibleBookName} = bibleDocumentInfo!;

    addEventVerseInfo(event, {bookInitials, bibleBookName, bibleDocumentInfo, ...verseInfo})
}

const {config} = useCommon();
</script>

<style lang="scss">
@import "~@/common.scss";

.linebreak {
  display: block;
}
</style>
