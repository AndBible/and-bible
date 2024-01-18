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
  <div :class="{hasActions, horizontal: !vertical, vertical}">
    <div v-if="hasButton('BOOKMARK')" class="large-action" @click="addBookmark">
      <FontAwesomeLayers>
        <FontAwesomeIcon icon="bookmark"/>
        <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
      </FontAwesomeLayers>
      <div class="title">{{ strings.addBookmark }}</div>
    </div>
    <div v-if="hasButton('BOOKMARK_NOTES')" class="large-action" @click="addNote">
      <FontAwesomeLayers>
        <FontAwesomeIcon icon="edit"/>
        <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
      </FontAwesomeLayers>
      <div class="title">{{ vertical ? strings.verseNoteLong : strings.verseNote }}</div>
    </div>
    <div v-if="hasButton('MY_NOTES')" class="large-action" @click="openMyNotes">
      <FontAwesomeIcon icon="file-alt"/>
      <div class="title">{{ strings.verseMyNotes }}</div>
    </div>
    <div v-if="hasButton('SHARE')" class="large-action" @click="share">
      <FontAwesomeIcon icon="share-alt"/>
      <div class="title">{{ vertical ? strings.verseShareLong : strings.verseShare }}</div>
    </div>
    <div v-if="hasButton('COMPARE')" class="large-action" @click="compare">
      <FontAwesomeIcon icon="custom-compare"/>
      <div class="title">{{ vertical ? strings.verseCompareLong : strings.verseCompare }}</div>
    </div>
    <div v-if="hasButton('SPEAK')" class="large-action" @click="speak">
      <FontAwesomeIcon icon="headphones"/>
      <div class="title">{{ strings.verseSpeak }}</div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject} from "vue";
import {FontAwesomeIcon, FontAwesomeLayers} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";
import {androidKey, keyboardKey, modalKey} from "@/types/constants";
import {SelectionInfo} from "@/types/common";
import {BibleModalButtonId, GenericModalButtonId} from "@/composables/config";

const props = withDefaults(defineProps<{
    selectionInfo: SelectionInfo
    vertical: boolean
    hasActions: boolean
}>(), {
    vertical: false,
    hasActions: false
})

const emit = defineEmits(["close"]);
const {closeModals} = inject(modalKey)!
const {setupKeyboardListener} = inject(keyboardKey)!
const {strings, appSettings} = useCommon()

const selectionInfo = computed(() => props.selectionInfo);
const android = inject(androidKey)!;

const verseInfo = computed(() => selectionInfo.value?.verseInfo || null);
const ordinalInfo = computed(() => selectionInfo.value?.ordinalInfo || null);
const startOrdinal = computed(() => selectionInfo.value && selectionInfo.value.startOrdinal);
const endOrdinal = computed(() => selectionInfo.value && selectionInfo.value.endOrdinal);

const modalButtons = computed(() => {
    if(verseInfo.value) {
        return appSettings.bibleModalButtons;
    } else {
        return appSettings.genericModalButtons;
    }
});

function hasButton(buttonId: BibleModalButtonId|GenericModalButtonId) {
    return modalButtons.value.includes(buttonId);
}


function share() {
    if(verseInfo.value) {
        android.shareVerse(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value);
    }
}

function addBookmark() {
    if(verseInfo.value) {
        android.addBookmark(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value, false);
    } else if(ordinalInfo.value) {
        android.addGenericBookmark(ordinalInfo.value.bookInitials, ordinalInfo.value.osisRef, startOrdinal.value, endOrdinal.value, false);
    }
    emit("close");
}

function compare() {
    if(verseInfo.value) {
        android.compare(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value);
    }
}

function addNote() {
    if(verseInfo.value) {
        android.addBookmark(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value, true);
    } else if(ordinalInfo.value) {
        android.addGenericBookmark(ordinalInfo.value.bookInitials, ordinalInfo.value.osisRef, startOrdinal.value, endOrdinal.value, true);
    }
    emit("close");
}

function openMyNotes() {
    if(verseInfo.value) {
        android.openMyNotes(verseInfo.value.v11n!, startOrdinal.value);
    }
}

function speak() {
    if(verseInfo.value) {
        android.speak(verseInfo.value.bookInitials, verseInfo.value.v11n!, startOrdinal.value, endOrdinal.value);
    } else if(ordinalInfo.value) {
        android.speakGeneric(ordinalInfo.value.bookInitials, ordinalInfo.value.osisRef, startOrdinal.value, endOrdinal.value);
    }
    closeModals()
}

setupKeyboardListener((e: KeyboardEvent) => {
    console.log("AmbiguousActionButtons keyboard listener", e);
    if (e.key.toLowerCase() === "b") {
        addBookmark();
        return true;
    } else if (e.key.toLowerCase() === "n") {
        addNote();
        return true;
    } else if (e.code === "Space") {
        speak();
        return true;
    }
    return false;
}, 5)
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.large-action {
  cursor: pointer;
  min-width: 40px; // Ensures dynamic plus icon has sufficient space to be appended
  display: flex;
  flex-direction: row;

  .horizontal & {
    flex-direction: column;
    font-size: 60%;
    margin: 0 auto 0 auto;
  }

  .vertical & {
    @extend .light;
    @extend .button;
  }

  .fa-layers, .svg-inline--fa {
    //    padding-inline-end: 14px;  // Causes non-alignment of the icons in the verse action dialog.
    .horizontal & {
      color: $button-grey;
      margin: 0 auto 0 auto;
      padding-bottom: 5px;
      $size: 20px;
      width: $size;
      height: $size;
    }
  }

  .title {
    margin: 0 auto 0 auto;
  }

  padding-bottom: 0.5em;

  .horizontal & {
    .hasActions & {
      padding-bottom: 5px;
    }
  }
}

.horizontal {
  display: flex;
  flex-direction: row;
  justify-content: space-evenly;
  flex-wrap: wrap;
}
</style>
