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
  <div :style="parentStyle" class="editable-text">
    <div class="editor-container" v-if="editMode">
      <TextEditor :text="editText || ''" @changed="textChanged" @close="editMode = false"/>
    </div>
    <template v-else>
      <div :class="{'notes-display': true, constraintHeight}" @click="handleClicks">
        <div v-html="editText || ''"/>
      </div>
    </template>
  </div>
</template>

<script>
import {ref} from "@vue/reactivity";
import TextEditor from "@/components/TextEditor";
import {watch} from "@vue/runtime-core";

let cancelOpen = () => {}

export default {
  name: "EditableText",
  components: {TextEditor},
  emits: ["closed", "changed", "opened"],
  props: {
    editDirectly:{type: Boolean, default: false},
    text:{type: String, default: null},
    maxHeight: {type: String, default: null},
    constraintHeight: {type: Boolean, default: false},
  },
  setup(props, {emit}) {
    const editMode = ref(props.editDirectly);
    const parentStyle = ref("");
    const editText = ref(props.text);
    parentStyle.value = props.maxHeight ? `--max-height: ${props.maxHeight};`: "--max-height: 100pt;";

    function cancelFunc() {
      editMode.value = false;
    }
    watch(editMode, mode => {
      if(!mode) emit("closed", editText.value);
      else {
        emit("opened")
        if(cancelFunc !== cancelOpen) {
          cancelOpen()
        }
        cancelOpen = cancelFunc
      }
    })
    watch(() => props.text, t => {
      editText.value = t;
    })

    function textChanged(newText) {
      editText.value = newText
      emit("changed", newText);
    }

    function handleClicks(event) {
      if(event.target.nodeName !== "A") {
        editMode.value = true;
      }
    }

    return {editMode, parentStyle, editText, textChanged, handleClicks}
  }
}
</script>

<style lang="scss" scoped>
@import "~@/common.scss";
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
  @extend .journal-button;
  position: absolute;
  height: 20pt;
  width: 20pt;
  right: 0;
  top: 0;
}
.editable-text {
  position: relative;
  min-height: 1.5em;
}
</style>
<style>
.editable-text ul {
  margin-top: 5pt;
  margin-bottom: 5pt;
  padding-left: 15pt;
}
</style>
