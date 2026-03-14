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
  <div class="whole-page-bookmarks">
    <div
        v-for="bookmark in visibleBookmarks"
        :key="bookmark.id"
        class="whole-page-bookmark-item"
        :style="{ color: getColor(bookmark) }"
        @click.stop="openBookmark(bookmark)"
    >
      <FontAwesomeIcon :icon="getIcon(bookmark)" />
      <span v-if="bookmark.hasNote" class="note-indicator">
        <FontAwesomeIcon :icon="faEdit" size="xs" />
      </span>
    </div>
    <div class="add-bookmark-button" @click.stop="createBookmark">
      <FontAwesomeIcon :icon="faPlus" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from "vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faBookmark, faEdit, faPlus } from "@fortawesome/free-solid-svg-icons";
import { androidKey, globalBookmarksKey } from "@/types/constants";
import { BaseBookmark, GenericBookmark } from "@/types/client-objects";
import { isGenericBookmark, isWholePageBookmark, resolveIcon as resolveIconUtil } from "@/composables/bookmarks";
import { useCommon } from "@/composables";
import { emit } from "@/eventbus";

const props = defineProps<{
  bookInitials: string;
  bookKey: string;
}>();

const globalBookmarks = inject(globalBookmarksKey)!;
const android = inject(androidKey)!;
const { config, appSettings, adjustedColor } = useCommon();

const wholePageBookmarks = computed(() => {
  return globalBookmarks.bookmarks.value.filter(b =>
    isGenericBookmark(b) &&
    b.bookInitials === props.bookInitials &&
    b.key === props.bookKey &&
    isWholePageBookmark(b)
  ) as GenericBookmark[];
});

const visibleBookmarks = computed(() => {
  const hideLabels = new Set(config.bookmarksHideLabels);
  return wholePageBookmarks.value.filter(b => {
    if (!config.showBookmarks && !(b.hasNote && config.showMyNotes)) return false;
    const labelIds = new Set(b.labels);
    for (const labelId of labelIds) {
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
  return adjustedColor(appSettings.monochromeMode ? "black" : label.color).string();
}

function getIcon(bookmark: BaseBookmark) {
  const label = getLabel(bookmark);
  if (!label) return faBookmark;
  const resolved = resolveIconUtil(bookmark, label);
  return resolved ?? faBookmark;
}

function openBookmark(bookmark: BaseBookmark) {
  emit("bookmark_clicked", bookmark.id);
}

function createBookmark() {
  android.createWholePageBookmark(props.bookInitials, props.bookKey);
}
</script>

<style lang="scss" scoped>
.whole-page-bookmarks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75em;
  padding: 0.75em;
  margin-top: 1em;
  border-top: 1px solid rgba(128, 128, 128, 0.3);
}

.whole-page-bookmark-item {
  display: flex;
  align-items: center;
  gap: 0.25em;
  padding: 0.25em 0.5em;
  border-radius: 4px;
  background: rgba(128, 128, 128, 0.1);
  cursor: pointer;
  font-size: 1.2em;

  &:hover {
    background: rgba(128, 128, 128, 0.2);
  }

  .note-indicator {
    margin-left: 0.25em;
    opacity: 0.7;
  }
}

.add-bookmark-button {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.25em 0.5em;
  border-radius: 4px;
  background: rgba(128, 128, 128, 0.1);
  cursor: pointer;
  font-size: 1.2em;
  color: rgba(128, 128, 128, 0.7);

  &:hover {
    background: rgba(128, 128, 128, 0.2);
    color: rgba(128, 128, 128, 1);
  }
}
</style>
