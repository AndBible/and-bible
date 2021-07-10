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
      <span class="highlight-transition" :class="{timeout, isHighlighted: !timeout && highlighted}">
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
import {addEventVerseInfo, cancellableTimer, getVerseInfo} from "@/utils";
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
    if(verseInfo) {
      verseInfo.showStack = reactive([shown]);
      provide("verseInfo", verseInfo);
    }

    const {register, registerEndHighlight} = inject("verseHighlight");

    const ordinal = computed(() => {
      return parseInt(props.verseOrdinal);
    });

    register(ordinal.value, {highlight});

    const book = computed(() => {
      return props.osisID.split(".")[0]
    });

    const chapter = computed(() => {
      return parseInt(props.osisID.split(".")[1])
    });

    const verse = computed(() => {
      return parseInt(props.osisID.split(".")[2])
    });

    const {bookInitials, bibleBookName, originalOrdinalRange, ordinalRange} = inject("bibleDocumentInfo", {})

    const fromBibleDocument = computed(() => !!ordinalRange);

    const timeout = ref(false);
    const cancelFuncs = [];

    function endHighlight() {
      cancelFuncs.forEach(f => f());
      cancelFuncs.splice(0);
      timeout.value = false;
      highlighted.value = false;
    }

    const highlighted = ref(false);

    function setupEndHighlight() {
      const [promise, cancel] = cancellableTimer(fadeReferenceDelay);
      promise.then(() => timeout.value = true)
      cancelFuncs.push(cancel);
    }

    function highlight() {
      endHighlight();
      highlighted.value = true;
      setupEndHighlight();
      registerEndHighlight(endHighlight);
    }

    if(originalOrdinalRange && ordinal.value <= originalOrdinalRange[1] && ordinal.value >= originalOrdinalRange[0]) {
      highlight()
    }

    function verseClicked(event) {
      if(!fromBibleDocument.value) return;
      addEventVerseInfo(event, {bookInitials, bibleBookName, ...verseInfo})
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
      fromBibleDocument,
      verseClicked,
      ...common,
    }
  },
}
</script>

<style scoped lang="scss">
.linebreak {
  display: block;
}

.highlight-transition {
  transition: background-color 0.5s ease;
  &.timeout {
    transition: background-color 5s ease;
  }
}

.isHighlighted {
  background-color: rgba(255, 230, 0, 0.4);
  .night & {
    background-color: rgba(255, 230, 0, 0.6);
  }
}
</style>
