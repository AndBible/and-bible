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
  <div
      :id="`v-${ordinal}`"
      class="verse bookmarkStyle"
      :class="{noLineBreak: !config.showVersePerLine}"
  >
    <VerseNumber v-if="shown && config.showVerseNumbers && verse !== 0" :verse-num="verse"/>
    <div class="inlineDiv" ref="contentTag"><slot/></div>
  </div>
</template>

<script>
import {inject, provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {addAll, getVerseInfo} from "@/utils";

export default {
  name: "Verse",
  components: {VerseNumber},
  props: {
    osisID: { type: String, required: true},
    verseOrdinal: { type: String, required: true},
  },
  setup(props) {
    const verseInfo = getVerseInfo(props);

    const shown = ref(true);
    verseInfo.showStack = reactive([shown]);

    //const {bookmarksForWholeVerse, styleForLabels} = inject("bookmarks");
    //const {bookmarkLabels} = inject("globalBookmarks");

    provide("verseInfo", verseInfo);

    const common = useCommon();
    //const undoHighlights = [];
    return {
      //styleForLabels,
      //undoHighlights,
      shown,
      ...common,
      //globalBookmarks: bookmarksForWholeVerse,
      //globalBookmarkLabels: bookmarkLabels
    }
  },
  computed: {
    // TODO: this is not very fast as we do same filtering for each bookmark.
//    bookmarks({globalBookmarks, ordinal}) {
//      return globalBookmarks.filter(({ordinalRange}) => (ordinalRange[0] <= ordinal) && (ordinal <= ordinalRange[1]))
//    },
//    bookmarkLabels({bookmarks, globalBookmarkLabels}) {
//      const labels = new Set();
//      for(const b of bookmarks) {
//        addAll(labels, ...b.labels);
//      }
//      return Array.from(labels).map(l => globalBookmarkLabels.get(l)).filter(v => v);
//    },
//    bookmarkStyle({bookmarkLabels}) {
//      return this.styleForLabels(bookmarkLabels)
//    },
    ordinal() {
      return parseInt(this.verseOrdinal);
    },
    book() {
      return this.osisID.split(".")[0]
    },
    chapter() {
      return parseInt(this.osisID.split(".")[1])
    },
    verse() {
      return parseInt(this.osisID.split(".")[2])
    },
  },
}
</script>

<style scoped>
.noLineBreak {
  display: inline;
}

.bookmarkStyle {
  border-radius: 0.2em;
}
</style>
