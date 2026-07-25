<!--
  - Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <ModalDialog v-if="showModal" @close="close" blocking locate-top>
    <template #title>
      {{ mode === "menu" ? strings.headingMenuTitle : (payload?.kind === "add" ? strings.addHeadingLong : strings.editHeading) }}
    </template>
    <div v-if="mode === 'menu'" class="buttons">
      <button class="button light" @click="startEdit">
        <FontAwesomeIcon icon="edit"/>
        {{ strings.editHeading }}
      </button>
      <button v-if="existingOverride" class="button light" @click="restoreOriginal">
        <FontAwesomeIcon icon="undo"/>
        {{ strings.restoreHeading }}
      </button>
      <button class="button light" @click="deleteHeading">
        <FontAwesomeIcon icon="trash"/>
        {{ strings.deleteHeading }}
      </button>
    </div>
    <div v-else class="edit-form">
      <input
          ref="inputRef"
          v-model="editText"
          class="heading-input"
          :placeholder="strings.headingTextPlaceholder"
          @keyup.enter="save"
      />
      <div class="level-label">{{ strings.headingLevel }}</div>
      <div class="level-selector">
        <button
            v-for="l in 6"
            :key="l"
            class="level-btn"
            :class="{selected: editLevel === l}"
            @click="editLevel = l"
        >H{{ l }}</button>
      </div>
      <div class="edit-buttons">
        <button class="button" :disabled="!editText.trim()" @click="save">{{ strings.save }}</button>
        <button class="button light" @click="close">{{ strings.cancel }}</button>
      </div>
    </div>
  </ModalDialog>
</template>

<script lang="ts" setup>
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {computed, inject, nextTick, ref} from "vue";
import {setupEventBusListener} from "@/eventbus";
import {useCommon} from "@/composables";
import {androidKey, customHeadingsKey} from "@/types/constants";
import {headingOverrideKey, HeadingMenuPayload} from "@/composables/custom-headings";

const {strings} = useCommon();
const android = inject(androidKey)!;
const {customHeadings, headingOverrides} = inject(customHeadingsKey)!;

const showModal = ref(false);
const mode = ref<"menu" | "edit">("menu");
const payload = ref<HeadingMenuPayload | null>(null);
const editText = ref("");
const editLevel = ref(3);
const inputRef = ref<HTMLInputElement | null>(null);

const customHeading = computed(() =>
    payload.value?.kind === "custom" ? customHeadings.get(payload.value.headingId) : undefined);

const existingOverride = computed(() => {
    if (payload.value?.kind !== "module") return undefined;
    const {bookInitials, ordinal, titleIndex} = payload.value;
    return headingOverrides.get(headingOverrideKey(bookInitials, ordinal, titleIndex));
});

setupEventBusListener("open_heading_menu", (p: HeadingMenuPayload) => {
    payload.value = p;
    if (p.kind === "add") {
        editText.value = "";
        editLevel.value = 3;
        mode.value = "edit";
    } else {
        mode.value = "menu";
    }
    showModal.value = true;
});

function startEdit() {
    const p = payload.value;
    if (!p) return;
    if (p.kind === "custom") {
        const h = customHeading.value;
        if (!h) return;
        editText.value = h.text;
        editLevel.value = h.level;
    } else if (p.kind === "module") {
        editText.value = existingOverride.value?.newText ?? p.text;
        editLevel.value = existingOverride.value?.newLevel ?? p.level;
    }
    mode.value = "edit";
    nextTick(() => inputRef.value?.focus());
}

function save() {
    const p = payload.value;
    const text = editText.value.trim();
    if (!p || !text) return;
    if (p.kind === "add") {
        android.addCustomHeading(p.bookInitials, p.v11n, p.ordinal, editLevel.value, text);
    } else if (p.kind === "custom") {
        android.updateCustomHeading(p.headingId, editLevel.value, text);
    } else if (p.kind === "module") {
        android.setHeadingOverride(p.bookInitials, p.v11n, p.ordinal, p.titleIndex, text, editLevel.value, false);
    }
    close();
}

function deleteHeading() {
    const p = payload.value;
    if (!p) return;
    if (p.kind === "custom") {
        android.deleteCustomHeading(p.headingId);
    } else if (p.kind === "module") {
        android.setHeadingOverride(p.bookInitials, p.v11n, p.ordinal, p.titleIndex, null, 0, true);
    }
    close();
}

function restoreOriginal() {
    if (existingOverride.value) {
        android.removeHeadingOverride(existingOverride.value.id);
    }
    close();
}

function close() {
    showModal.value = false;
    payload.value = null;
}
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.buttons {
  display: flex;
  flex-direction: column;
  gap: 2pt;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 8pt;
}

.heading-input {
  font-size: 110%;
  padding: 6pt;
  border: 1px solid #888;
  border-radius: 4pt;
  background: inherit;
  color: inherit;
  width: 100%;
  box-sizing: border-box;
}

.level-label {
  font-size: 90%;
  opacity: 0.7;
}

.level-selector {
  display: flex;
  gap: 4pt;
}

.level-btn {
  flex: 1;
  padding: 6pt 0;
  border: 1px solid #888;
  border-radius: 4pt;
  background: none;
  color: inherit;
  cursor: pointer;

  &.selected {
    background-color: $button-grey;
    color: white;
    border-color: $button-grey;
  }
}

.edit-buttons {
  display: flex;
  gap: 8pt;
  justify-content: flex-end;
}
</style>
