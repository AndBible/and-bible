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
  <div :id="osisID" :class="{noLineBreak: !config.versePerLine}"><VerseNumber v-if="shown && config.verseNumbers && verse !== 0" :verse-num="verse"/><div class="inlineDiv" ref="contentTag"><slot/></div></div>
</template>

<script>
import TagMixin from "@/components/TagMixin";
import {provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {getVerseInfo} from "@/utils";

export default {
  name: "Verse",
  components: {VerseNumber},
  props: {
    osisID: { type: String, required: true},
  },
  setup(props) {
    const verseInfo = getVerseInfo(props.osisID);

    const shown = ref(true);
    verseInfo.showStack = reactive([shown]);

    provide("verseInfo", verseInfo);
    const common = useCommon(props);
    return {shown, ...common}
  },
  mixins: [TagMixin],
  computed: {
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
