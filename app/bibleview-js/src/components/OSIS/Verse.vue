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
    <span
        :id="`v-${ordinal}`"
        class="verse"
    >
      <span class="highlight-transition" :class="{isHighlighted: !timeout && (highlighted || isInOriginalOrdinalRange)}">
        <VerseNumber v-if="shown && config.showVerseNumbers && verse !== 0" :verse-num="verse"/><slot/>
      </span>
    </span>
  <span :class="{linebreak: config.showVersePerLine}"/>
</template>

<script>
import {inject, provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {getVerseInfo, sleep} from "@/utils";
import {computed} from "@vue/reactivity";
import {fadeReferenceDelay} from "@/constants";

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
    const verseMap = inject("verseMap");

    const ordinal = computed(() => {
      return parseInt(props.verseOrdinal);
    });

    verseMap.register(ordinal.value, {highlight});

    const book = computed(() => {
      return props.osisID.split(".")[0]
    });

    const chapter = computed(() => {
      return parseInt(props.osisID.split(".")[1])
    });

    const verse = computed(() => {
      return parseInt(props.osisID.split(".")[2])
    });

    const {originalOrdinalRange} = inject("bibleDocumentInfo", {})

    const timeout = ref(false);

    const isInOriginalOrdinalRange = computed(() => {
      if(!originalOrdinalRange) return false
      return ordinal.value <= originalOrdinalRange[1] && ordinal.value >= originalOrdinalRange[0];
    });

    const highlighted = ref(false);

    function highlight() {
      timeout.value = false;
      highlighted.value = true;
      sleep(fadeReferenceDelay).then(() => timeout.value = true)
    }

    if(isInOriginalOrdinalRange.value) {
      sleep(fadeReferenceDelay).then(() => timeout.value = true)
    }

    const common = useCommon();
    return {
      timeout,
      ordinal,
      book,
      chapter,
      verse,
      shown,
      highlighted,
      isInOriginalOrdinalRange,
      ...common,
    }
  },
}
</script>

<style scoped>
.linebreak {
  display: block;
}

.highlight-transition {
  border-radius: 5pt;
  transition: background-color 1s ease;
}

.isHighlighted {
  background-color: rgba(255, 230, 0, 0.4);
}
</style>
