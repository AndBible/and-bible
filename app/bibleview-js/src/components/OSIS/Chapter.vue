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
  <div class="chapter-number ordinal" :data-ordinal="ordinal" v-if="config.showChapterNumbers && startTag">{{sprintf(strings.chapterNum, chapterNum)}}</div>
  <slot/>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";

export default {
  name: "Chapter",
  props: {
    n: {type: String, default: null},
    osisID: {type: String, default: null},
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    chapterTitle: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "chapterTitle")
    const bibleDocumentInfo = inject("bibleDocumentInfo");
    const ordinal = computed(() => {
      const ordinalRange = bibleDocumentInfo.originalOrdinalRange || bibleDocumentInfo.ordinalRange;
      return ordinalRange[0];
    });

    return {ordinal, ...useCommon()};
  },
  computed: {
    startTag: ({eID}) => eID === null,
    chapterNum: ({n, osisID}) => n || osisID.split(".")[1]
  }
}
</script>

<style scoped>
.chapter-number {
  color: Gray;
  font-size: 70%;
	margin-top: 1em;
	margin-bottom: 0.5em;
	text-align: center;
}
</style>
