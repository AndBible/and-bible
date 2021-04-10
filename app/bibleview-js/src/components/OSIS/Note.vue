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
  <AmbiguousSelection ref="ambiguousSelection"/>
  <Modal @close="showNote = false" v-if="showNote">
    <div @click="ambiguousSelection.handle">
      <slot/>
      <OpenAllLink :v11n="v11n"/>
    </div>
    <template #title>
      <template v-if="isFootNote">
        {{ sprintf(strings.noteText, typeStr) }}

      </template>
      <template v-else-if="isCrossReference">
        {{ strings.crossReferenceText }}
      </template>
      <template v-else>
        {{ strings.otherNoteText }}
      </template>
    </template>
  </Modal>
  <span
      v-if="(config.showCrossReferences && isCrossReference) || (config.showFootNotes && isFootNote) || isOther"
      class="skip-offset">
    <span :class="{noteHandle: true, isFootNote, isCrossReference, isOther}" @click="noteClicked">
      {{handle}}
    </span>
  </span>
</template>

<script>
import {checkUnsupportedProps, useCommon, useReferenceCollector} from "@/composables";
import Modal from "@/components/modals/Modal";
import {get} from "lodash";
import {ref, provide, inject} from "@vue/runtime-core";
import {addEventFunction} from "@/utils";
import OpenAllLink from "@/components/OpenAllLink";

let count = 0;
const alphabets = "abcdefghijklmnopqrstuvwxyz"

function runningHandle() {
  return alphabets[count++%alphabets.length];
}

export default {
  name: "Note",
  components: {OpenAllLink, Modal},
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
    isFootNote: ({type}) => ["explanation", "translation", "study", "variant", "alternative", "x-editor-correction"].includes(type),
    typeStr: ({type, typeStrings, strings}) => get(typeStrings, type, strings.footnoteTypeUndefined),
    isCrossReference: ({type}) => type === "crossReference",
    isOther: ({isCrossReference, isFootNote}) => !isCrossReference && !isFootNote
  },
  setup(props) {
    const ambiguousSelection = ref(null);
    checkUnsupportedProps(props, "resp");
    checkUnsupportedProps(props, "placement", ['foot']);
    checkUnsupportedProps(props, "type",
        ["explanation", "translation", "crossReference", "variant", "alternative", "study", "x-editor-correction"]);
    checkUnsupportedProps(props, "subType",
        ["x-gender-neutral", 'x-original', 'x-variant-adds', 'x-bondservant']);
    const {strings, ...common} = useCommon();
    const showNote = ref(false);
    function noteClicked(event) {
      addEventFunction(event,
          () => {
            referenceCollector.clear();
            showNote.value = true;
          },
          {title: strings.openFootnote, priority: 10});
    }
    const typeStrings = {
      explanation: strings.footnoteTypeExplanation,
      translation: strings.footnoteTypeTranslation,
      study: strings.footnoteTypeStudy,
      variant: strings.footnoteTypeVariant,
      alternative: strings.footnoteTypeAlternative,
    };
    const {v11n} = inject("osisFragment", {})
    const referenceCollector = useReferenceCollector();
    provide("referenceCollector", referenceCollector);

    return {strings, typeStrings, showNote, noteClicked, ambiguousSelection, v11n, ...common};
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.note-handle-base {
  @extend .superscript;
  padding: 0.2em;
}

.isCrossReference {
  @extend .note-handle-base;
  color: orange;
}

.open-all {
  padding-top: 1em;
}

.isFootNote {
  @extend .note-handle-base;
  color: #b63afd;
}

.isOther {
  @extend .note-handle-base;
  color: #209546;
}
</style>
