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
  <div :style="bookmarkStyle" :id="osisID" :class="{noLineBreak: !config.versePerLine}"><VerseNumber v-if="shown && config.verseNumbers && verse !== 0" :verse-num="verse"/><div class="inlineDiv" ref="contentTag"><slot/></div></div>
</template>

<script>
import TagMixin from "@/components/TagMixin";
import {inject, provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {getVerseInfo} from "@/utils";

export default {
  name: "Verse",
  components: {VerseNumber},
  props: {
    osisID: { type: String, required: true},
    verseOrdinal: { type: String, required: true},
  },
  setup(props) {
    const verseInfo = getVerseInfo(props.osisID);

    const shown = ref(true);
    verseInfo.ordinal = parseInt(props.verseOrdinal);
    verseInfo.showStack = reactive([shown]);
    const bookmarks = inject("bookmarks");
    const bookmarkLabels = inject("bookmarkLabels");
    provide("verseInfo", verseInfo);
    const common = useCommon(props);

    return {shown, ...common, globalBookmarks: bookmarks, globalBookmarkLabels: bookmarkLabels}
  },
  mixins: [TagMixin],
  computed: {
    bookmarks: ({globalBookmarks, ordinal}) =>
        globalBookmarks.bookmarks.filter(({range}) => (range[0] <= ordinal) && (ordinal <= range[1])),
    bookmarkLabels({bookmarks, globalBookmarkLabels}) {
      const labels = new Set();
      for(const b of bookmarks) {
        for(const l of b.labels) {
          labels.add(l);
        }
      }
      return Array.from(labels).map(l => globalBookmarkLabels.labels.get(l));
    },
    bookmarkStyle({bookmarkLabels}) {
      const colors = [];
      for(const s of bookmarkLabels) {
        const c = `rgba(${s.color[0]}, ${s.color[1]}, ${s.color[2]}, 20%)`
        colors.push(c);
      }
      return `background-image: linear-gradient(to bottom, ${colors.join(",")})`;
    },
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
  }
}
</script>

<style scoped>
.noLineBreak {
  display: inline;
}
</style>
