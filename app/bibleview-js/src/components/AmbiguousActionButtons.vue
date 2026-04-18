<!--
  - Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div v-if="showMoreMenu" @click.stop="showMoreMenu = false" class="modal-backdrop no-background"/>
  <div ref="containerRef" class="horizontal" :class="{hasActions}">
    <!-- Primary buttons that are always visible -->
    <template v-for="button in primaryButtons" :key="button">
      <ActionButton
        v-if="hasButton(button)" 
        :button="button" 
        @click="handleButtonClick(button)"
        ref="buttonRefs"
      />
    </template>

    <!-- More options button -->
    <div v-if="secondaryButtons.length > 0" class="large-action" @click.stop="showMoreMenu = true" @touchstart.stop>
      <FontAwesomeIcon :icon="faEllipsisV"/>
      <div class="title">{{ strings.more }}</div>
    </div>

    <!-- Dropdown menu for secondary buttons -->
    <div v-if="showMoreMenu" ref="moreMenuRef" class="dropdown-menu" :class="{'locate-bottom': !locateTop}" @click.stop>
      <template v-for="button in secondaryButtons" :key="button">
        <ActionButton
          v-if="hasButton(button)" 
          :button="button" 
          @click="handleButtonClick(button)"
        />
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject, nextTick, onMounted, ref} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";
import {androidKey, keyboardKey, locateTopKey, memorizationKey, modalKey} from "@/types/constants";
import {SelectionInfo} from "@/types/common";
import {ModalButtonId} from "@/composables/config";
import {faEllipsisV} from "@fortawesome/free-solid-svg-icons";
import ActionButton from "@/components/ActionButton.vue";

const props = withDefaults(defineProps<{
    selectionInfo: SelectionInfo
    hasActions: boolean
}>(), {
    hasActions: false
})

const emit = defineEmits(["close"]);
const {closeModals} = inject(modalKey)!
const {setupKeyboardListener} = inject(keyboardKey)!
const locateTop = inject(locateTopKey);

const {strings, appSettings, isExperimentalFeatureEnabled} = useCommon()

const selectionInfo = computed(() => props.selectionInfo);
const android = inject(androidKey)!;
const memorization = inject(memorizationKey)!;

const verseInfo = computed(() => selectionInfo.value?.verseInfo || null);
const ordinalInfo = computed(() => selectionInfo.value?.ordinalInfo || null);
const startOrdinal = computed(() => selectionInfo.value && selectionInfo.value.startOrdinal);
const endOrdinal = computed(() => selectionInfo.value && selectionInfo.value.endOrdinal);

const showMoreMenu = ref(false);
const moreMenuRef = ref<HTMLElement | null>(null);
const containerRef = ref<HTMLElement | null>(null);
const buttonRefs = ref<any[]>([]);

// How many buttons to show before using a "more" menu
const visibleButtonCount = ref(4);

const modalButtons = computed<ModalButtonId[]>(() => {
    let allButtons: ModalButtonId[]
    if(verseInfo.value) {
         allButtons = ["BOOKMARK", "BOOKMARK_NOTES", "MY_NOTES", "SHARE", "COMPARE", "SPEAK", "MEMORIZE", "REMOVE_MEMORIZATION_TARGET", "ADD_PARAGRAPH_BREAK", "LLM_ACTION"];
    } else {
         allButtons = ["BOOKMARK", "BOOKMARK_NOTES", "SPEAK", "ADD_PARAGRAPH_BREAK", "LLM_ACTION"];
    }
    if (!isExperimentalFeatureEnabled("add_paragraph_break")) {
        allButtons = allButtons.filter(b => b !== "ADD_PARAGRAPH_BREAK");
    }
    if (startOrdinal.value != null) {
        // Only show REMOVE_MEMORIZATION_TARGET when all selected ordinals are already targeted
        const effectiveEnd = endOrdinal.value ?? startOrdinal.value;
        let allTargeted = true;
        for (let i = startOrdinal.value; i <= effectiveEnd; i++) {
            if (!memorization.targets.has(i)) { allTargeted = false; break; }
        }
        if (!allTargeted) {
            allButtons = allButtons.filter(b => b !== "REMOVE_MEMORIZATION_TARGET");
        }
    }
    if (!appSettings.llmConfigured) {
        allButtons = allButtons.filter(b => b !== "LLM_ACTION");
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

async function recalculateVisibleButtons() {
    if (!containerRef.value) {
        visibleButtonCount.value = modalButtons.value.length;
        return;
    }

    // Wait for the DOM to update so we can measure elements
    await nextTick();

    const containerWidth = containerRef.value.clientWidth;
    const moreButtonWidth = 70; // Estimated width of "more" button
    const buttonElements = buttonRefs.value.filter(el => el); // Filter out any undefined refs

    if (buttonElements.length === 0) {
        // Default to a reasonable number if we can't measure
        visibleButtonCount.value = 4;
        return;
    }

    // Calculate average button width from existing buttons
    let totalButtonWidth = 0;
    for (const buttonEl of buttonElements) {
        if (buttonEl.$el) {
            totalButtonWidth += buttonEl.$el.offsetWidth;
        }
    }
    const avgButtonWidth = totalButtonWidth / buttonElements.length;

    const maxButtonsWithoutMore = Math.floor(containerWidth / avgButtonWidth);
    
    if (maxButtonsWithoutMore >= modalButtons.value.length) {
        visibleButtonCount.value = modalButtons.value.length;
    } else {
        const maxButtonsWithMore = Math.floor((containerWidth - moreButtonWidth) / avgButtonWidth);
        visibleButtonCount.value = Math.max(1, maxButtonsWithMore);
    }
}

// Primary buttons are shown directly
const primaryButtons = computed<ModalButtonId[]>(() => {
    if (modalButtons.value.length <= visibleButtonCount.value) {
        return modalButtons.value;
    } else {
        return modalButtons.value.slice(0, visibleButtonCount.value);
    }
});

// Secondary buttons are shown in the dropdown
const secondaryButtons = computed(() => {
    if (modalButtons.value.length <= visibleButtonCount.value) {
        return [];
    } else {
        return modalButtons.value.slice(visibleButtonCount.value);
    }
});

// Recalculate visible buttons when component mounts and whenever the window resizes
onMounted(() => {
    recalculateVisibleButtons();
    window.addEventListener('resize', recalculateVisibleButtons);
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
        case 'REMOVE_MEMORIZATION_TARGET':
            removeMemorizationTarget();
            break;
        case 'SPEAK':
            speak();
            break;
        case 'ADD_PARAGRAPH_BREAK':
            addParagraphBreak();
            break;
        case 'LLM_ACTION':
            llmAction();
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

function removeMemorizationTarget() {
    if(verseInfo.value) {
        android.removeMemorizationTarget(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value);
    }
    emit("close");
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

function addParagraphBreak() {
    if(verseInfo.value) {
        android.addParagraphBreakBookmark(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value);
    } else if(ordinalInfo.value) {
        android.addGenericParagraphBreakBookmark(ordinalInfo.value.bookInitials, ordinalInfo.value.osisRef, startOrdinal.value, endOrdinal.value);
    }
    emit("close");
}

function llmAction() {
    const selectedText = window.getSelection()?.toString() ?? "";
    if(verseInfo.value) {
        android.llmAction(verseInfo.value.bookInitials, startOrdinal.value, endOrdinal.value, selectedText);
    } else if(ordinalInfo.value) {
        android.llmActionGeneric(ordinalInfo.value.bookInitials, ordinalInfo.value.osisRef, startOrdinal.value, endOrdinal.value, selectedText);
    }
    emit("close");
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
@use "@/common.scss" as *;

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
