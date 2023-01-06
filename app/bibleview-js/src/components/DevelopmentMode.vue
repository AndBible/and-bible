<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
  <div :style="`position: fixed; top:0; width:100%;  background-color: rgba(100, 255, 100, 0.7);
               height:${appSettings.topOffset}px`"
  >
    Current verse: {{currentVerse}}
  </div>
  <div v-if="config.developmentMode" class="highlightButton">
    <span @mouseenter="testMakeBookmark">Get selection!</span>
  </div>
</template>

<script lang="ts" setup>
import {useCommon} from "@/composables";
import {emit} from "@/eventbus";
import {inject} from "vue";
import {androidKey} from "@/types/constants";
import {QuerySelection} from "@/composables/android";

withDefaults(defineProps<{currentVerse: number|null}>(), {currentVerse: null});

const android = inject(androidKey)!;

let lblCount = 0;

function testMakeBookmark() {
  const selection = android.querySelection()
  if(!selection || selection instanceof String) return
  const s = selection as QuerySelection
  const bookmark = {
    id: -lblCount -1,
    ordinalRange: [s.startOrdinal, s.endOrdinal],
    offsetRange: [s.startOffset, s.endOffset],
    bookInitials: s.bookInitials,
    note: "Test!",
    labels: [-(lblCount++ % 5) - 1]
  }
  emit("add_or_update_bookmarks", [bookmark])
  emit("remove_ranges")
}
const {config, appSettings} = useCommon();

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
