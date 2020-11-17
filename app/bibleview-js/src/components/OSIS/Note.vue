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
  <template v-if="(config.showCrossReferences && isCrossReference) || (config.showFootnotes && isFootNote)">
    <span :class="{noteHandle: true, isFootNote, isCrossReference}" @click="showNote = !showNote">{{handle}}</span>
    <Modal @close="showNote = false" v-show="showNote">
      <slot/>
      <template #title>
        <template v-if="isFootNote">
          {{strings.noteText}}
        </template>
        <template v-else>
          {{strings.crossReferenceText}}
        </template>
      </template>
    </Modal>
  </template>
</template>

<script>
import {useCommon} from "@/composables";
import Modal from "@/components/Modal";

let count = 0;
const alphabets = "abcdefghijklmnopqrstuvwxyz"

function runningHandle() {
  return alphabets[count++%alphabets.length];
}

export default {
  name: "Note",
  components: {Modal},
  props: {
    osisID: {type: String, default: null},
    osisRef: {type: String, default: null},
    placement: {type: String, default: null},
    type: {type: String, default: null},
    subType: {type: String, default: null},
    n: {type: String, default: null},
  },
  data() {
    return {
      showNote: false
    }
  },
  computed: {
    handle: ({n}) => n || runningHandle(),
    isFootNote: ({type}) => ["explanation", "translation"].includes(type),
    isCrossReference: ({type}) => type === "crossReference"
  },
  setup(props) {
    return useCommon(props);
  },
}
</script>

<style scoped lang="scss">
.noteHandleBase {
  vertical-align: top;
  font-size: 0.7em;
  padding: 0.5em;
}

.isCrossReference {
  @extend .noteHandleBase;
  color: orange;
}

.isFootNote {
  @extend .noteHandleBase;
  color: #b63afd;
}
</style>
