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
    <slot/><span class="skip-offset">&nbsp;</span>
  </div>
</template>

<script>
import {provide, reactive, ref} from "@vue/runtime-core";
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
    const verseInfo = getVerseInfo(props);

    const shown = ref(true);
    verseInfo.showStack = reactive([shown]);

    provide("verseInfo", verseInfo);

    const common = useCommon();
    return {
      shown,
      ...common,
    }
  },
  computed: {
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
