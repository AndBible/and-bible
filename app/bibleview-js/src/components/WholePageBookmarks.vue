<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
      v-for="bookmark in visibleBookmarks"
      :key="bookmark.id"
      class="journal-button bookmark-item"
      :style="{ color: getColor(bookmark) }"
      @click.stop="openItem(bookmark)"
  >
    <FontAwesomeIcon :icon="getIcon(bookmark)" />
    <span v-if="bookmark.hasNote" class="note-indicator">
      <FontAwesomeIcon :icon="faEdit" size="xs" />
    </span>
  </div>
  <div class="journal-button add-bookmark-button" @click.stop="createBookmark">
    <FontAwesomeIcon :icon="faPlus" />
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from "vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faBookmark, faEdit, faPlus } from "@fortawesome/free-solid-svg-icons";
import { androidKey, globalBookmarksKey } from "@/types/constants";
import { BaseBookmark } from "@/types/client-objects";
import { isAiDocMarker, isWholePageItem, resolveIcon as resolveIconUtil } from "@/composables/bookmarks";
import { useCommon } from "@/composables";
import { emit } from "@/eventbus";

const props = defineProps<{
  bookInitials: string;
  bookKey: string;
}>();

const globalBookmarks = inject(globalBookmarksKey)!;
const android = inject(androidKey)!;
const { config, appSettings, adjustedColor } = useCommon();

const wholePageItems = computed(() => {
  return globalBookmarks.bookmarks.value.filter(b => isWholePageItem(b, props.bookInitials, props.bookKey));
});

const visibleBookmarks = computed(() => {
  const hideLabels = new Set(config.bookmarksHideLabels);
  return wholePageItems.value.filter(b => {
    if (isAiDocMarker(b)) return config.showAiDocMarkers;
    if (!config.showBookmarks && !(b.hasNote && config.showMyNotes)) return false;
    for (const labelId of b.labels) {
      if (hideLabels.has(labelId)) return false;
    }
    return true;
  });
});

function getLabel(bookmark: BaseBookmark) {
  const labelId = bookmark.primaryLabelId || bookmark.labels[0];
  return globalBookmarks.bookmarkLabels.get(labelId);
}

function getColor(bookmark: BaseBookmark) {
  const label = getLabel(bookmark);
  if (!label) return "gray";
  return adjustedColor((appSettings.monochromeMode && !appSettings.colorEinkMode) ? "black" : label.color).string();
}

function getIcon(bookmark: BaseBookmark) {
  const label = getLabel(bookmark);
  if (!label) return faBookmark;
  const resolved = resolveIconUtil(bookmark, label);
  return resolved ?? faBookmark;
}

function openItem(bookmark: BaseBookmark) {
  if (isAiDocMarker(bookmark)) {
    window.android.openAiDocPage(bookmark.documentInitials, bookmark.pageKey);
  } else {
    emit("bookmark_clicked", bookmark.id);
  }
}

function createBookmark() {
  android.createWholePageBookmark(props.bookInitials, props.bookKey);
}

defineExpose({visibleBookmarks});
</script>

<style lang="scss" scoped>
.bookmark-item {
  .note-indicator {
    margin-left: 0.15em;
    opacity: 0.7;
  }
}

.add-bookmark-button {
  color: rgba(128, 128, 128, 0.7);
}
</style>
