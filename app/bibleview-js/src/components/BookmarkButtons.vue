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
  <div style="display: flex; justify-content: space-between;" @click.stop>
    <div class="bookmark-buttons">
      <div class="bookmark-button" @click="assignLabels">
        <FontAwesomeIcon icon="tags"/>
      </div>
      <div class="bookmark-button" :class="{highlighted: bookmark.hasNote}" @click="$emit('info-clicked')">
        <FontAwesomeIcon icon="edit"/>
      </div>
      <div class="bookmark-button" @click="shareVerse">
        <FontAwesomeIcon icon="share-alt"/>
      </div>
      <div :class="{highlighted: bookmark.wholeVerse}" class="bookmark-button" @click="toggleWholeVerse">
        <FontAwesomeIcon icon="text-width"/>
      </div>
      <template v-if="showStudyPadButtons">
        <div
          v-for="label of labels.filter(l => l.isRealLabel)"
          :key="label.id"
          :style="`color: ${adjustedColor(label.color)};`"
          class="bookmark-button"
          @click.stop="openStudyPad(label.id)"
        >
          <FontAwesomeIcon icon="file-alt"/>
        </div>
      </template>
    </div>
    <div class="bookmark-buttons" style="align-self: end;">
      <div class="bookmark-button end" @click="removeBookmark">
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

export default {
  name: "BookmarkButtons",
  props: {
    bookmark: {type: Object, required: true},
    showStudyPadButtons: {type: Boolean, default: false},
  },
  emits: ["close-bookmark", "info-clicked"],
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

    function openStudyPad(labelId) {
      android.openStudyPad(labelId);
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        emit("close-bookmark");
        android.removeBookmark(bookmark.value.id);
      }
    }

    return {
      toggleWholeVerse, shareVerse, assignLabels, removeBookmark, areYouSure, labels, openStudyPad,
      ...useCommon()
    }
  }
}
</script>

<style scoped>

</style>
