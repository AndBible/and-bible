<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <div :style="`position: fixed; top:0; width:100%;  background-color: rgba(100, 255, 100, 0.7);
               height:${config.topOffset}px`"
  >
    Current verse: {{currentVerse}}
  </div>
  <div v-if="config.developmentMode" class="highlightButton">
    <span @mouseenter="testMakeBookmark">Get selection!</span>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {emit, Events} from "@/eventbus";
import {inject} from "vue";

export default {
  name: "DevelopmentMode",
  props: {
    currentVerse: {type: Number, default: null}
  },
  setup() {
    const android = inject("android");

    let lblCount = 0;

    function testMakeBookmark() {
      const selection = android.querySelection()
      if(!selection) return
      const bookmark = {
        id: -lblCount -1,
        ordinalRange: [selection.startOrdinal, selection.endOrdinal],
        offsetRange: [selection.startOffset, selection.endOffset],
        bookInitials: selection.bookInitials,
        note: "Test!",
        labels: [-(lblCount++ % 5) - 1]
      }
      emit(Events.ADD_OR_UPDATE_BOOKMARKS, [bookmark])
      emit(Events.REMOVE_RANGES)
    }

    return {testMakeBookmark, ...useCommon()};
  },
}
</script>

<style scoped>
.highlightButton {
  position: fixed;
  bottom: 0;
  left: 0;
  padding: 2em;
  background: yellow;
}
</style>
