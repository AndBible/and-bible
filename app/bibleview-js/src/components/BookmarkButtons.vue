<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div class="button-container" :class="{ambiguous: !inBookmarkModal}">
    <div class="bookmark-buttons">
      <div
        v-if="!inBookmarkModal"
        class="bookmark-button"
        :style="`color: ${buttonColor(primaryLabel.color)};`"
      >
        <FontAwesomeIcon icon="bookmark"/>
      </div>
      <div
        v-if="!inBookmarkModal"
        class="bookmark-button"
        @click.stop="$emit('edit-clicked')"
        :style="`color: ${buttonColor(primaryLabel.color, bookmark.hasNote)};`"
      >
        <FontAwesomeIcon icon="edit"/>
      </div>
      <div
        class="bookmark-button"
        @click.stop="shareVerse"
        :style="`color: ${buttonColor(primaryLabel.color)};`"
      >
        <FontAwesomeIcon icon="share-alt"/>
      </div>
      <div
        class="bookmark-button"
        @click.stop="toggleWholeVerse"
        :style="`color: ${buttonColor(primaryLabel.color, bookmark.wholeVerse)};`"
      >
        <FontAwesomeIcon icon="text-width"/>
      </div>
      <template v-if="showStudyPadButtons">
        <div
          v-for="label of labels.filter(l => l.isRealLabel)"
          :key="label.id"
          :style="`color: ${buttonColor(label.color)};`"
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
  <AreYouSure ref="areYouSure">
    <template #title>
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
</template>

<script>
import {useCommon} from "@/composables";
import {computed, ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import AreYouSure from "@/components/modals/AreYouSure";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import Color from "color";

export default {
  name: "BookmarkButtons",
  props: {
    bookmark: {type: Object, required: true},
    showStudyPadButtons: {type: Boolean, default: false},
    inBookmarkModal: {type: Boolean, default: false},
  },
  emits: ["close-bookmark", "edit-clicked"],
  components: {AreYouSure, FontAwesomeIcon},
  setup(props, {emit}) {
    const areYouSure = ref(null);
    const bookmark = computed(() => props.bookmark);
    const android = inject("android");

    function toggleWholeVerse() {
      android.setBookmarkWholeVerse(bookmark.value.id, !bookmark.value.wholeVerse);
    }

    function shareVerse() {
      android.shareBookmarkVerse(bookmark.value.id);
    }

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
    }

    const {bookmarkLabels} = inject("globalBookmarks");

    const labels = computed(() => {
      return bookmark.value.labels.map(id => bookmarkLabels.get(id));
    });

    const primaryLabel = computed(() => {
      const primaryLabelId = bookmark.value.primaryLabelId || bookmark.value.labels[0];
      return bookmarkLabels.get(primaryLabelId);
    });

    function openStudyPad(labelId) {
      android.openStudyPad(labelId, bookmark.value.id);
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        emit("close-bookmark");
        android.removeBookmark(bookmark.value.id);
      }
    }

    function buttonColor(color, highlighted = false) {
      let col = Color(color);
      if(highlighted) {
        col = col.darken(0.5);
      }
      return col.hsl().string();
    }

    return {
      toggleWholeVerse, shareVerse, assignLabels, removeBookmark, areYouSure, labels, openStudyPad, primaryLabel, buttonColor,
      ...useCommon()
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.button-container {
  display: flex;
  justify-content: space-between;

  &.ambiguous {
    border-radius: 0 0 $button-border-radius $button-border-radius;
    background-color: $modal-content-background-color;
    margin: calc(-#{$button-padding} + 1.5px);
    .night & {
      background-color: $modal-content-background-color-night;
    }
  }
}
.bookmark-button {
  font-size: 25px;
  color: $button-grey;
  padding: 5px;
  &.end {
    align-self: flex-end;
  }
  &.highlighted {
    // opacity: 0.5;
  }
}
</style>
