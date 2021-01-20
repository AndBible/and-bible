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
      v-if="(config.showCrossReferences && isCrossReference) || (config.showFootNotes && isFootNote)"
      class="inlineDiv skip-offset"
  >
    <span :class="{noteHandle: true, isFootNote, isCrossReference}" @click="noteClicked">
      {{handle}}
    </span>
    <Modal @close="showNote = false" v-if="showNote">
      <slot/>
      <template #title>
        {{isFootNote ? sprintf(strings.noteText, typeStr) : strings.crossReferenceText }}
      </template>
    </Modal>
  </div>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import Modal from "@/components/Modal";
import {get} from "lodash";
import {ref} from "@vue/runtime-core";
import {addEventFunction} from "@/utils";

let count = 0;
const alphabets = "abcdefghijklmnopqrstuvwxyz"

function runningHandle() {
  return alphabets[count++%alphabets.length];
}

export default {
  name: "Note",
  components: {Modal},
  noContentTag: true,
  props: {
    osisID: {type: String, default: null},
    osisRef: {type: String, default: null},
    placement: {type: String, default: null},
    type: {type: String, default: null},
    subType: {type: String, default: null},
    n: {type: String, default: null},
    resp: {type: String, default: null},
  },
  computed: {
    handle: ({n}) => n || runningHandle(),
    isFootNote: ({type}) => ["explanation", "translation", "study", "variant", "alternative"].includes(type),
    typeStr: ({type, typeStrings, strings}) => get(typeStrings, type, strings.footnoteTypeUndefined),
    isCrossReference: ({type}) => type === "crossReference"
  },
  setup(props) {
    checkUnsupportedProps(props, "resp");
    checkUnsupportedProps(props, "placement");
    checkUnsupportedProps(props, "type",
        ["explanation", "translation", "crossReference", "variant", "alternative", "study"]);
    checkUnsupportedProps(props, "subType",
        ["x-gender-neutral", 'x-original', 'x-variant-adds', 'x-bondservant']);
    const {strings, ...common} = useCommon();
    const showNote = ref(false);
    function noteClicked(event) {
      addEventFunction(event,
          () => {showNote.value = !showNote.value},
          {title: strings.openFootnote, priority: 20});
    }
    const typeStrings = {
      explanation: strings.footnoteTypeExplanation,
      translation: strings.footnoteTypeTranslation,
      study: strings.footnoteTypeStudy,
      variant: strings.footnoteTypeVariant,
      alternative: strings.footnoteTypeAlternative,
    };
    return {strings, typeStrings, showNote, noteClicked, ...common};
  },
}
</script>

<style scoped lang="scss">
.note-handle-base {
  vertical-align: top;
  font-size: 0.7em;
  padding: 0.5em;
}

.isCrossReference {
  @extend .note-handle-base;
  color: orange;
}

.isFootNote {
  @extend .note-handle-base;
  color: #b63afd;
}
</style>
