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
    <div v-if="config.showMarkAsReadButton && isExperimentalFeatureEnabled('reading_and_memorization')" class="mark-as-read-container">
      <div class="button" :class="{read: chapterRead}" @click="onMarkAsRead">
        <FontAwesomeIcon :icon="faCheck"/> {{ chapterRead ? sprintf(strings.chapterMarkedRead, displayChapter) : sprintf(strings.markChapterRead, displayChapter) }}
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
const {id, bibleBookName, bookInitials, bookmarks, ordinalRange, originalOrdinalRange, v11n, osisRef} = props.document;

provide(bibleDocumentInfoKey, {bibleBookName, bookInitials, ordinalRange, originalOrdinalRange, v11n})

const containerRef = ref<HTMLElement | null>(null);

const globalBookmarks = inject(globalBookmarksKey)!;
globalBookmarks.updateBookmarks(bookmarks);

const memorization = inject(memorizationKey)!;
if (props.document.memorizedOrdinals) {
    memorization.mergeData(props.document.memorizedOrdinals, props.document.targetOrdinals ?? []);
}
memorization.setupIndicatorRendering(containerRef, id);

const {config, appSettings, strings, sprintf, isExperimentalFeatureEnabled, ...common} = useCommon();

useBookmarks(id, ordinalRange, globalBookmarks, bookInitials,  null, true, ref(true), common, config, appSettings);

let footNoteCount = ordinalRange[0] || 0;

function getFootNoteCount() {
    return footNoteCount++;
}

provide(footnoteCountKey, {getFootNoteCount});
const displayChapter = Math.max(1, props.document.chapterNumber);

const {chapterRead, toggleChapterRead: onMarkAsRead} = useReadingTracker(
    containerRef, bookInitials, ordinalRange, displayChapter, props.document.chapterRead ?? false
);

</script>

<style scoped>
.bible-document {
    position: relative;
}

.mark-as-read-container {
    text-align: center;
    padding: 12px 0;

    .button.read {
        background-color: #4CAF50;
        cursor: default;
    }
}
</style>
