<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <div :style="parentStyle">
    <div class="edit-button" @click="editMode = !editMode">
      <FontAwesomeIcon icon="edit"/>
    </div>
    <div class="editor-container" v-if="editMode">
      <TextEditor :text="text || ''" @changed="$emit('changed', $event)"/>
    </div>
    <template v-else>
      <div :class="{'notes-display': true, constraintHeight}">
        <div v-html="text || ''"/>
      </div>
    </template>
  </div>
</template>

<script>
import {ref} from "@vue/reactivity";
import TextEditor from "@/components/TextEditor";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {watch} from "@vue/runtime-core";

export default {
  name: "EditableText",
  components: {TextEditor, FontAwesomeIcon},
  emits: ["changed", "closed"],
  props: {
    editDirectly:{type: Boolean, default: false},
    text:{type: String, default: null},
    maxHeight: {type: String, default: null},
    constraintHeight: {type: Boolean, default: false},
  },
  setup(props, {emit}) {
    const editMode = ref(props.editDirectly);
    const parentStyle = ref("");
    parentStyle.value = props.maxHeight ? `--max-height: ${props.maxHeight};`: "--max-height: 100pt;";
    watch(editMode, mode => {
      if(!mode) emit("closed");
    })
    return {editMode, parentStyle}
  }
}
</script>

<style lang="scss" scoped>
.notes-display {
  width: calc(100% - 22pt);
}

.constraintHeight {
  overflow-y: auto;
  max-height: calc(var(--max-height) - 17px);
}

.editor-container {
  max-width: calc(100% - 22pt);
}
.edit-button {
  position: absolute;
  height: 20pt;
  width: 20pt;
  right: 5px;
  color: #939393;
}

</style>
