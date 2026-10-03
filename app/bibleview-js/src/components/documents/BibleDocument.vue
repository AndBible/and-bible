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
  <div
      ref="containerRef"
      :id="`doc-${document.id}`"
       class="document bible-document"
       :data-book-initials="bookInitials"
       :data-osis-ref="osisRef"
  >
    <Chapter v-if="document.addChapter" :n="document.chapterNumber.toString()"/>
    <OsisFragment :fragment="document.osisFragment"/>
    <div v-if="config.showMarkAsReadButton" class="mark-as-read-container">
      <div class="mark-as-read-wrapper">
        <FontAwesomeIcon
            class="mark-as-read-icon"
            :class="{read: chapterReadCount > 0}"
            :icon="faCheck"
            @click="onCheckClick"
            @touchstart.passive="onCheckPressStart"
            @touchend="onCheckPressEnd"
            @touchmove="onCheckPressEnd"
            @touchcancel="onCheckPressEnd"
            @contextmenu.prevent="onOpenReadHistory"
        />
        <span
            v-if="chapterReadCount > 0"
            class="read-count"
            @click="onOpenReadHistory"
        >×{{ chapterReadCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {inject, provide, ref} from "vue";
import {useBookmarks} from "@/composables/bookmarks";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import {useCommon} from "@/composables";
import Chapter from "@/components/OSIS/Chapter.vue";
import {bibleDocumentInfoKey, footnoteCountKey, globalBookmarksKey, memorizationKey} from "@/types/constants";
import {BibleDocumentType} from "@/types/documents";
import {useReadingTracker} from "@/composables/reading-tracker";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faCheck} from "@fortawesome/free-solid-svg-icons";

const props = defineProps<{ document: BibleDocumentType }>();

// eslint-disable-next-line no-unused-vars,vue/no-setup-props-destructure
const {id, bibleBookName, bookInitials, bookmarks, aiDocMarkers, ordinalRange, originalOrdinalRange, v11n, osisRef} = props.document;

provide(bibleDocumentInfoKey, {bibleBookName, bookInitials, ordinalRange, originalOrdinalRange, v11n})

const containerRef = ref<HTMLElement | null>(null);

const globalBookmarks = inject(globalBookmarksKey)!;
globalBookmarks.updateBookmarks([...bookmarks, ...aiDocMarkers]);

const memorization = inject(memorizationKey)!;
if (props.document.memorizedOrdinals) {
    memorization.mergeData(props.document.memorizedOrdinals, props.document.targetOrdinals ?? []);
}
memorization.setupIndicatorRendering(containerRef, id);

const {config, appSettings, ...common} = useCommon();

useBookmarks(id, ordinalRange, globalBookmarks, bookInitials,  null, true, ref(true), common, config, appSettings);

let footNoteCount = ordinalRange[0] || 0;

function getFootNoteCount() {
    return footNoteCount++;
}

provide(footnoteCountKey, {getFootNoteCount});
const displayChapter = Math.max(1, props.document.chapterNumber);

const {
    chapterReadCount,
    toggleChapterRead: onMarkAsRead,
    openChapterReadHistory: onOpenReadHistory,
} = useReadingTracker(
    containerRef, bookInitials, ordinalRange, displayChapter,
    props.document.chapterReadCount ?? 0,
);

// Long-press the check icon to open the read-history dialog. We track the
// press manually because the WebView's text-selection callout otherwise
// hijacks the gesture and shows the selection menu instead.
const LONG_PRESS_MS = 500;
let longPressTimer: number | null = null;
let longPressed = false;

function onCheckPressStart() {
    longPressed = false;
    longPressTimer = window.setTimeout(() => {
        longPressTimer = null;
        longPressed = true;
        onOpenReadHistory();
    }, LONG_PRESS_MS);
}

function onCheckPressEnd() {
    if (longPressTimer != null) {
        window.clearTimeout(longPressTimer);
        longPressTimer = null;
    }
}

function onCheckClick(event: Event) {
    if (longPressed) {
        longPressed = false;
        event.preventDefault();
        return;
    }
    onMarkAsRead();
}

</script>

<style scoped>
.bible-document {
    position: relative;
}

.mark-as-read-container {
    text-align: center;
    padding: 8px 0;
}

.mark-as-read-wrapper {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    position: relative;
    user-select: none;
    -webkit-user-select: none;
    -webkit-touch-callout: none;
}

.mark-as-read-icon {
    cursor: pointer;
    font-size: 18px;
    color: rgba(0, 0, 0, 0.3);
    padding: 6px;
    border-radius: 50%;

    .night & {
        color: rgba(255, 255, 255, 0.3);
    }

    .monochrome & {
        color: black;
        border: 1px solid rgba(0, 0, 0, 0.4);
    }

    .monochrome.night & {
        color: white;
        border: 1px solid rgba(255, 255, 255, 0.4);
    }

    &.read {
        color: #4CAF50;
        cursor: default;

        .night & {
            color: #66BB6A;
        }

        .monochrome & {
            color: black;
            border: 2px solid black;
        }

        .monochrome.night & {
            color: white;
            border: 2px solid white;
        }
    }
}

.read-count {
    cursor: pointer;
    font-size: 12px;
    font-weight: bold;
    margin-left: 2px;
    color: #4CAF50;

    .night & {
        color: #66BB6A;
    }

    .monochrome & {
        color: black;
    }

    .monochrome.night & {
        color: white;
    }
}
</style>
