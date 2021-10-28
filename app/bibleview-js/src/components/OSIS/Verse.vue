<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <span :id="`v-${ordinal}`" @click="verseClicked">
    <span
      :id="fromBibleDocument ? `o-${ordinal}` : null"
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

<script>
import {inject, provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {addEventVerseInfo, getVerseInfo} from "@/utils";
import {computed} from "@vue/reactivity";

export default {
  name: "Verse",
  components: {VerseNumber},
  props: {
    osisID: { type: String, required: true},
    verseOrdinal: { type: String, required: true},
  },
  setup(props) {
    const bibleDocumentInfo = inject("bibleDocumentInfo", {})
    const {bookInitials, bibleBookName, originalOrdinalRange, ordinalRange, v11n} = bibleDocumentInfo;
    const verseInfo = {...getVerseInfo(props), v11n};

    const shown = ref(true);
    if(verseInfo) {
      verseInfo.showStack = reactive([shown]);
      provide("verseInfo", verseInfo);
    }

    const {highlightedVerses, highlightVerse} = inject("verseHighlight");

    const ordinal = computed(() => {
      return parseInt(props.verseOrdinal);
    });

    const book = computed(() => {
      return props.osisID.split(".")[0]
    });

    const chapter = computed(() => {
      return parseInt(props.osisID.split(".")[1])
    });

    const verse = computed(() => {
      return parseInt(props.osisID.split(".")[2])
    });

    const fromBibleDocument = computed(() => !!ordinalRange);

    const highlighted = computed(() => highlightedVerses.has(ordinal.value))

    if(originalOrdinalRange && ordinal.value <= originalOrdinalRange[1] && ordinal.value >= originalOrdinalRange[0]) {
      highlightVerse(ordinal.value)
    }

    function verseClicked(event) {
      if(!fromBibleDocument.value) return;
      addEventVerseInfo(event, {bookInitials, bibleBookName, bibleDocumentInfo, ...verseInfo})
    }

    const common = useCommon();
    return {
      ordinal,
      book,
      chapter,
      verse,
      shown,
      highlighted,
      fromBibleDocument,
      verseClicked,
      ...common,
    }
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.linebreak {
  display: block;
}
</style>
