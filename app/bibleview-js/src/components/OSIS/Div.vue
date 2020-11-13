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
  <span class="paragraphBreak" ref="contentTag" v-if="isParagraph"/>
  <VerseNumber v-else-if="isPreVerse && shown" :verse-num="verseInfo.verse"/>
  <div v-else class="inlineDiv" ref="contentTag"><slot/></div>
</template>

<script>
import TagMixin from "@/components/TagMixin";
import {inject, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/utils";

const isPreVerse = ({type, subType}) => type === "x-milestone" && subType === "x-preverse";

export default {
  name: "Div",
  components: {VerseNumber},
  setup(props) {
    const verseInfo = inject("verseInfo", null);
    let shown = false;
    if(isPreVerse(props) && verseInfo) {
      shown = ref(true);
      for (const oldValue of verseInfo.showStack) {
        oldValue.value = false;
      }
      verseInfo.showStack.push(shown);
    }
    const common = useCommon(props);
    return {verseInfo, shown, ...common};
  },
  computed : {
    isParagraph: ({type}) => type === 'x-p',
    isPreVerse
  },
  props: {
    osisID: {type: String, default: null},
    sID: {type: String, default: null},
    eID: {type: String, default: null},
    type: {type: String, default: null},
    subType: {type: String, default: null},
    annotateRef: {type: String, default: null},
    annotateType: {type: String, default: null},
  },
  mixins: [TagMixin],
}
</script>

<style scoped>
  .paragraphBreak {
    display: block;
    height: 0.5em;
  }
</style>
