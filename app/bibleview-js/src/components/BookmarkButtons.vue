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
      <div :class="{highlighted: !bookmark.wholeVerse}" class="bookmark-button" @click="toggleWholeVerse">
        <FontAwesomeIcon icon="text-width"/>
      </div>
      <div v-if="!bookmark.wholeVerse && documentId" class="bookmark-button" @click="adjustRange">
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
import {inject, nextTick} from "@vue/runtime-core";
import AreYouSure from "@/components/modals/AreYouSure";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {findNodeAtOffsetWithNullOffset} from "@/utils";
import {emit, Events} from "@/eventbus";

export default {
  name: "BookmarkButtons",
  props: {
    bookmark: {type: Object, required: true},
    showStudyPadButtons: {type: Boolean, default: false},
    documentId: {type: String, default: null},
  },
  emits: ["close-bookmark", "info-clicked"],
  components: {AreYouSure, FontAwesomeIcon},
  setup(props, {emit: $emit}) {
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
        $emit("close-bookmark");
        android.removeBookmark(bookmark.value.id);
      }
    }

    async function adjustRange() {
      const documentId = props.documentId;
      const [startOrdinal, endOrdinal] = bookmark.value.ordinalRange;
      const [startOff, endOff] = bookmark.value.offsetRange;
      const firstElem = document.querySelector(`#doc-${documentId} #v-${startOrdinal}`);
      const secondElem = document.querySelector(`#doc-${documentId} #v-${endOrdinal}`);
      if (firstElem === null || secondElem === null) {
        console.error("Element is not found!", documentId, startOrdinal, endOrdinal);
        return;
      }
      const [first, startOff1] = findNodeAtOffsetWithNullOffset(firstElem, startOff);
      const [second, endOff1] = findNodeAtOffsetWithNullOffset(secondElem, endOff);
      emit(Events.CLOSE_MODALS);
      await nextTick();

      const range = new Range();
      range.setStart(first, startOff1);
      range.setEnd(second, endOff1);

      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      //android.adjustRange();
      console.log("clicking", firstElem, sel, range);
      //firstElem.click();
    }

    return {
      toggleWholeVerse, shareVerse, assignLabels, removeBookmark, areYouSure, labels, openStudyPad,
      adjustRange, ...useCommon()
    }
  }
}
</script>

<style scoped>

</style>
