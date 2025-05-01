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
  <div v-if="showMoreMenu" @click.stop="closeMoreMenu" class="modal-backdrop"/>
  <div :class="{hasActions, horizontal: !vertical, vertical}">
    <!-- Primary buttons that are always visible -->
    <template v-for="button in primaryButtons" :key="button">
      <div v-if="hasButton(button)" class="large-action" @click="handleButtonClick(button)">
        <FontAwesomeLayers v-if="button === 'BOOKMARK'">
          <FontAwesomeIcon icon="bookmark"/>
          <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
        </FontAwesomeLayers>
        <FontAwesomeLayers v-else-if="button === 'BOOKMARK_NOTES'">
          <FontAwesomeIcon icon="edit"/>
          <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
        </FontAwesomeLayers>
        <FontAwesomeIcon v-else-if="button === 'SHARE'" icon="share-alt"/>
        <FontAwesomeIcon v-else-if="button === 'MY_NOTES'" icon="file-alt"/>
        <FontAwesomeIcon v-else-if="button === 'COMPARE'" icon="custom-compare"/>
        <FontAwesomeIcon v-else-if="button === 'MEMORIZE'" :icon="faBrain"/>
        <FontAwesomeIcon v-else-if="button === 'SPEAK'" icon="headphones"/>
        <div class="title">
          <template v-if="button === 'BOOKMARK'">{{ strings.addBookmark }}</template>
          <template v-else-if="button === 'BOOKMARK_NOTES'">{{ vertical ? strings.verseNoteLong : strings.verseNote }}</template>
          <template v-else-if="button === 'SHARE'">{{ vertical ? strings.verseShareLong : strings.verseShare }}</template>
          <template v-else-if="button === 'MY_NOTES'">{{ strings.verseMyNotes }}</template>
          <template v-else-if="button === 'COMPARE'">{{ vertical ? strings.verseCompareLong : strings.verseCompare }}</template>
          <template v-else-if="button === 'MEMORIZE'">{{ vertical ? strings.verseMemorizeLong : strings.verseMemorize }}</template>
          <template v-else-if="button === 'SPEAK'">{{ strings.verseSpeak }}</template>

        </div>
      </div>
    </template>

    <!-- More options button -->
    <div v-if="secondaryButtons.length > 0" class="large-action" @click.stop="moreMenuClicked" @touchstart.stop>
      <FontAwesomeIcon :icon="faEllipsisV"/>
      <div class="title">{{ strings.more }}</div>
    </div>

    <!-- Dropdown menu for secondary buttons -->
    <div v-if="showMoreMenu" ref="moreMenuRef" class="dropdown-menu" :class="{'vertical-menu': vertical, 'locate-bottom': !locateTop}" @click.stop>
      <template v-for="button in secondaryButtons" :key="button">
        <div v-if="hasButton(button)" class="large-action" @click="handleButtonClick(button)">
          <FontAwesomeLayers v-if="button === 'BOOKMARK'">
            <FontAwesomeIcon icon="bookmark"/>
            <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
          </FontAwesomeLayers>
          <FontAwesomeLayers v-else-if="button === 'BOOKMARK_NOTES'">
            <FontAwesomeIcon icon="edit"/>
            <FontAwesomeIcon icon="plus" transform="shrink-5 down-6 right-12"/>
          </FontAwesomeLayers>
          <FontAwesomeIcon v-else-if="button === 'SHARE'" icon="share-alt"/>
          <FontAwesomeIcon v-else-if="button === 'MY_NOTES'" icon="file-alt"/>
          <FontAwesomeIcon v-else-if="button === 'COMPARE'" icon="custom-compare"/>
          <FontAwesomeIcon v-else-if="button === 'MEMORIZE'" :icon="faBrain"/>
          <FontAwesomeIcon v-else-if="button === 'SPEAK'" icon="headphones"/>
          
          <div class="title">
            <template v-if="button === 'BOOKMARK'">{{ strings.addBookmark }}</template>
            <template v-else-if="button === 'BOOKMARK_NOTES'">{{ vertical ? strings.verseNoteLong : strings.verseNote }}</template>
            <template v-else-if="button === 'SHARE'">{{ vertical ? strings.verseShareLong : strings.verseShare }}</template>
            <template v-else-if="button === 'MY_NOTES'">{{ strings.verseMyNotes }}</template>
            <template v-else-if="button === 'COMPARE'">{{ vertical ? strings.verseCompareLong : strings.verseCompare }}</template>
            <template v-else-if="button === 'MEMORIZE'">{{ vertical ? strings.verseMemorizeLong : strings.verseMemorize }}</template>
            <template v-else-if="button === 'SPEAK'">{{ strings.verseSpeak }}</template>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject, onMounted, onUnmounted, ref, watch} from "vue";
import {FontAwesomeIcon, FontAwesomeLayers} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";
import {androidKey, keyboardKey, locateTopKey, modalKey} from "@/types/constants";
import {SelectionInfo} from "@/types/common";
import {BibleModalButtonId, GenericModalButtonId, ModalButtonId} from "@/composables/config";
import {faBrain, faEllipsisV} from "@fortawesome/free-solid-svg-icons";
import {eventBus} from "@/eventbus";

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
const locateTop = inject(locateTopKey);

const {strings, appSettings} = useCommon()

const selectionInfo = computed(() => props.selectionInfo);
const android = inject(androidKey)!;

const verseInfo = computed(() => selectionInfo.value?.verseInfo || null);
const ordinalInfo = computed(() => selectionInfo.value?.ordinalInfo || null);
const startOrdinal = computed(() => selectionInfo.value && selectionInfo.value.startOrdinal);
const endOrdinal = computed(() => selectionInfo.value && selectionInfo.value.endOrdinal);

const showMoreMenu = ref(false);
const moreMenuRef = ref<HTMLElement | null>(null);

function closeMoreMenu() {
    showMoreMenu.value = false;
}

function moreMenuClicked(e: MouseEvent|TouchEvent) {
    showMoreMenu.value = true;
}

watch(showMoreMenu, v => {
    if (v) {
        eventBus.on("back_clicked", closeMoreMenu);
    } else {
        eventBus.off("back_clicked", closeMoreMenu);
    }

})

const modalButtons = computed<ModalButtonId[]>(() => {
    let allButtons: ModalButtonId[]
    if(verseInfo.value) {
         allButtons = ["BOOKMARK", "BOOKMARK_NOTES", "MY_NOTES", "SHARE", "COMPARE", "SPEAK", "MEMORIZE"];
    } else {
         allButtons = ["BOOKMARK", "BOOKMARK_NOTES", "SPEAK"];
    }
    let disabledButtons: ModalButtonId[];
    if(verseInfo.value) {
        disabledButtons = appSettings.disableBibleModalButtons;
    } else {
        disabledButtons = appSettings.disableGenericModalButtons;
    }
    const disabledButtonsSet = new Set(disabledButtons);
    return allButtons.filter(button => !disabledButtonsSet.has(button));
});

const primaryButtons = computed<ModalButtonId[]>(() => {
    if (modalButtons.value.length <= 5) {
        return modalButtons.value;
    } else {
        // If there are more than 5 buttons, show the first 4 as primary buttons
        return modalButtons.value.slice(0, 4);
    }
});

const secondaryButtons = computed(() => {
    if (modalButtons.value.length <= 5) {
        return [];
    } else {
        // If there are more than 5 primary buttons, show the first 4 as primary buttons and the rest as secondary buttons
        return modalButtons.value.slice(4);
    }
});

function hasButton(buttonId: ModalButtonId) {
    return modalButtons.value.includes(buttonId);
}

function handleButtonClick(buttonId: ModalButtonId) {
    // Close the more menu when an action is selected
    showMoreMenu.value = false;
    
    switch (buttonId) {
        case 'BOOKMARK':
            addBookmark();
            break;
        case 'BOOKMARK_NOTES':
            addNote();
            break;
        case 'SHARE':
            share();
            break;
        case 'MY_NOTES':
            openMyNotes();
            break;
        case 'COMPARE':
            compare();
            break;
        case 'MEMORIZE':
            memorize();
            break;
        case 'SPEAK':
            speak();
            break;
    }
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

function memorize() {
    if(verseInfo.value) {
        android.memorize(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value);
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
      .monochrome.night & {
        color: white;
      }
      margin: 0 auto 0 auto;
      padding-bottom: 5px;
      $size: 20px;
      width: $size;
      height: $size;
    }
  }

  .title {
    margin: 0 auto 0 auto;
    .monochrome.night & {
      color: white;
    }
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

@keyframes dropdown-animate {
  from {
    opacity: 0
  }
  to {
    opacity: 1
  }
}

.dropdown-menu {
  position: absolute;
  background-color: white;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
  padding: 8px;
  margin-top: 4px;
  min-width: 50px;
  right: 0;
  &.locate-bottom {
    bottom: 0;
  }
  animation-name: dropdown-animate;
  animation-duration: 0.2s;
  .noAnimation & {
    animation: none;
    box-shadow: none;
  }

  .night & {
    background-color: #333;
  }

  &.vertical-menu {
    position: relative;
    margin-top: 8px;
    width: 100%;
  }

  .large-action {
    padding: 8px;
    margin: 4px 0;
    border-radius: 4px;
    
    &:hover {
      background-color: rgba(0, 0, 0, 0.05);
      
      .night & {
        background-color: rgba(255, 255, 255, 0.1);
      }
    }
  }
}
</style>
