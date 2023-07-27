<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div :style="parentStyle" class="editable-text">
    <div class="editor-container" :class="{constraintDisplayHeight}" v-if="editMode">
      <TextEditor :text="editText || ''" @save="textChanged" @close="editMode = false"/>
    </div>
    <template v-else>
      <div v-if="editText" class="notes-display" :class="{constraintDisplayHeight}" @click="handleClicks">
        <div v-html="editText"/>
      </div>
      <div class="placeholder" v-else-if="showPlaceholder" @click="handleClicks">
        <slot>
          {{ strings.editTextPlaceholder }}
        </slot>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
let cancelOpen = () => {}
</script>

<script lang="ts" setup>
import {inject, ref, watch} from "vue";
import TextEditor from "@/components/TextEditor.vue";
import {useCommon} from "@/composables";
import {exportModeKey} from "@/types/constants";
import {Nullable} from "@/types/common";


const emit = defineEmits(["closed", "save", "opened"]);
const props = withDefaults(defineProps<{
    editDirectly: boolean
    showPlaceholder: boolean
    text: Nullable<string>
    maxEditorHeight: string
    constraintDisplayHeight: boolean
}>(), {
    editDirectly: false,
    showPlaceholder: false,
    text: null,
    maxEditorHeight: "inherit",
    constraintDisplayHeight: false
})

const editMode = ref<boolean>(props.editDirectly);
const parentStyle = ref(`--max-height: ${props.maxEditorHeight}; font-family: var(--font-family); font-size: var(--font-size);`);
const editText = ref(props.text);
const exportMode = inject(exportModeKey, ref(false));

function cancelFunc() {
    editMode.value = false;
}

watch(editMode, (mode, oldValue) => {
    if (!mode) {
        emit("closed", editText.value);
    }
    else {
        emit("opened")
        if (cancelFunc !== cancelOpen) {
            cancelOpen()
        }
        cancelOpen = cancelFunc
    }
}, {immediate: true})
watch(() => props.text, t => {
    editText.value = t;
})

watch(exportMode, mode => {
    if (mode) {
        editMode.value = false;
    }
});

function textChanged(newText: string) {
    editText.value = newText
    emit("save", newText);
}

function handleClicks(event: MouseEvent) {
    if ((event.target! as HTMLElement).nodeName !== "A") {
        editMode.value = true;
    }
}

const {strings} = useCommon();
defineExpose({editMode});
</script>

<style lang="scss" scoped>
@import '~@/lib/pell/pell.scss';
@import "~@/common.scss";

.notes-display {
  //  width: 100%;
  margin-bottom: 8pt;
  padding: 1px 7px 10px 7px;

  &.constraintDisplayHeight {
    @extend .visible-scrollbar;
    overflow-y: auto;
    max-height: calc(var(--max-height) - 17px);
  }
}

.placeholder {
  opacity: 0.5;
}

.editor-container {
  max-width: 100%;
  padding-top: 8pt;
  padding-bottom: 3pt;
  padding-inline-start: 0;

  &.constraintDisplayHeight {
    padding-top: 0;
    padding-bottom: 0;
  }
}

.edit-button {
  @extend .journal-button;
  position: absolute;
  height: 20pt;
  width: 20pt;

  [dir=ltr] & {
    right: 0;
  }

  [dir=rtl] & {
    left: 0;
  }

  top: 0;
}

.editable-text {
  position: relative;
  color: var(--text-color);
  background-color: var(--background-color);
}
</style>
<style lang="scss">
div.pell-content, .pell-content div, .notes-display div {
  margin-top: 5px;
}

.editable-text ul, ol, blockquote {
  margin-top: 5pt;
  margin-bottom: 5pt;
  margin-left: 0 !important;
  padding-left: 15pt !important;

  & ul, ol {
    margin-top: 0;
    margin-bottom: 0;
  }
}

.editable-text ul {
  padding-left: 12pt !important;
}

.editable-text .placeholder {
  padding: 15px;
}
</style>
