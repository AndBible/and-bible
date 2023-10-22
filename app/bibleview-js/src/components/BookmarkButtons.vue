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
  <div :class="{ambiguous: !inBookmarkModal}">
    <div class="button-container">
      <div class="bookmark-buttons">
        <div
            v-if="!inBookmarkModal"
            @click.stop="$emit('info-clicked')"
            class="bookmark-button"
            :style="buttonColor(primaryLabel.color)"
        >
          <FontAwesomeIcon icon="info-circle"/>
        </div>
        <div
            v-if="!inBookmarkModal"
            class="bookmark-button"
            @click.stop="$emit('edit-clicked')"
            :style="buttonColor(primaryLabel.color)"
        >
          <template v-if="bookmark.hasNote">
            <FontAwesomeIcon icon="pen-square"/>
          </template>
          <template v-else>
            <FontAwesomeIcon icon="edit"/>
          </template>
        </div>
        <div
            v-if="isBibleBookmark(bookmark)"
            class="bookmark-button"
            @click.stop="shareVerse"
            :style="buttonColor(primaryLabel.color)"
        >
          <FontAwesomeIcon icon="share-alt"/>
        </div>
        <div
            class="bookmark-button"
            @click.stop="toggleWholeVerse"
            :style="buttonColor(primaryLabel.color)"
        >
          <template v-if="bookmark.wholeVerse">
            <FontAwesomeIcon icon="custom-whole-verse-true"/>
          </template>
          <template v-else>
            <FontAwesomeIcon icon="custom-whole-verse-false"/>
          </template>
        </div>
        <template v-if="showStudyPadButtons">
          <div
              v-for="label of labels.filter(l => l.isRealLabel)"
              :key="label.id"
              :style="buttonColor(label.color)"
              class="bookmark-button"
              @click.stop="openStudyPad(label.id)"
          >
            <FontAwesomeIcon icon="file-alt"/>
          </div>
        </template>
      </div>
      <div class="bookmark-buttons" style="align-self: end;">
        <div class="bookmark-button end" @click.stop="removeBookmark">
          <FontAwesomeIcon icon="trash"/>
        </div>
      </div>
    </div>
  </div>

  <AreYouSure ref="areYouSure">
    <template #title>
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
</template>

<script lang="ts" setup>
import {useCommon} from "@/composables";
import {computed, inject, ref} from "vue";
import AreYouSure from "@/components/modals/AreYouSure.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import Color from "color";
import {sortBy} from "lodash";
import {androidKey, globalBookmarksKey} from "@/types/constants";
import {ColorParam} from "@/types/common";
import {BaseBookmark, LabelAndStyle} from "@/types/client-objects";
import {isBibleBookmark} from "@/composables/bookmarks";

const props = withDefaults(defineProps<{
    bookmark: BaseBookmark
    showStudyPadButtons: boolean
    inBookmarkModal: boolean
}>(), {
    showStudyPadButtons: false,
    inBookmarkModal: false
});

const emit = defineEmits(["close-bookmark", "edit-clicked", 'info-clicked']);

const areYouSure = ref<InstanceType<typeof AreYouSure> | null>(null);
const bookmark = computed(() => props.bookmark);
const android = inject(androidKey)!;

function toggleWholeVerse() {
    android.setBookmarkWholeVerse(bookmark.value, !bookmark.value.wholeVerse);
}

function shareVerse() {
    android.shareBookmarkVerse(bookmark.value);
}

const {bookmarkLabels} = inject(globalBookmarksKey)!;

const labels = computed<LabelAndStyle[]>(() => {
    return sortBy(bookmark.value.labels.map((id: IdType) => bookmarkLabels.get(id)!), ["name"]);
});

const primaryLabel = computed(() => {
    const primaryLabelId = bookmark.value.primaryLabelId || bookmark.value.labels[0];
    return bookmarkLabels.get(primaryLabelId)!;
});

function openStudyPad(labelId: IdType) {
    android.openStudyPad(labelId, bookmark.value);
}

async function removeBookmark() {
    if (await areYouSure.value!.areYouSure()) {
        emit("close-bookmark");
        android.removeBookmark(bookmark.value);
    }
}

function buttonColor(color: ColorParam, highlighted = false) {
    if (props.inBookmarkModal) {
        return ""
    }
    let col = Color(color);
    if (col.isLight()) {
        col = col.darken(0.5);
    }
    if (highlighted) {
        col = col.alpha(0.7);
    }
    return `color:${col.hsl().string()};`;
}

const {strings} = useCommon();
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.button-container {
  display: flex;
  justify-content: space-between;
}

.ambiguous {
  @extend .visible-scrollbar;
  overflow-x: auto;
  border-radius: 0 0 $button-border-radius $button-border-radius;
  background-color: $modal-content-background-color;
  margin: calc(-#{$button-padding} + 1.5px);

  .night & {
    background-color: $modal-content-background-color-night;
  }
}

.bookmark-button {
  cursor: pointer;
  font-size: 25px;
  color: $button-grey;
  padding: 5px;

  &.end {
    align-self: flex-end;
  }

  &.highlighted {
    opacity: 0.7;
  }
}
</style>
