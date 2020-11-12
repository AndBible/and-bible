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

<template><div class="inlineDiv"><sup :class="{noteHandle: true, isFootNote, isCrossReference}" @click="showNote = !showNote">{{handle}}</sup><div class="noteBlock" v-show="showNote" ref="contentTag"><slot/></div></div></template>

<script>
import TagMixin from "@/components/TagMixin";

let count = 0;
const alphabets = "abcdefghijklmnopqrstuvwxyz"

function runningHandle() {
  return alphabets[count++%alphabets.length];
}

export default {
  name: "Note",
  mixins: [TagMixin],
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
  }

}
</script>

<style scoped>
.noteBlock {
  border: 15px red;
  border-radius: 6px;
  padding: 15px;
  background-color: lightyellow;
}
.noteHandle {
}
.isCrossReference {
  color: orange;
}
.isFootNote {
  color: #019c1a;
}
</style>
