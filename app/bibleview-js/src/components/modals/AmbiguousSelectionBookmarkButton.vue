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
  <div class="ambiguous-button" :style="buttonStyle" @click.stop="openBookmark">
    <div>
      {{ bookmark.verseRangeAbbreviated }} <q v-if="bookmark.text"><i>{{ abbreviated(bookmark.text, 30)}}</i></q>
    </div>
    <LabelList :bookmark-id="bookmarkId"/>
    <div style="display: flex; justify-content: space-between; height: 30px;">
      <div style="display: flex;">
        <div class="bookmark-button" @click.stop="assignLabels">
          <FontAwesomeIcon icon="tags"/>
        </div>
        <div class="bookmark-button" :class="{highlighted: bookmark.hasNote}" @click.stop="editNotes">
          <FontAwesomeIcon icon="edit"/>
        </div>
        <div class="bookmark-button" @click.stop="bookmarkInfo">
          <FontAwesomeIcon icon="info-circle"/>
        </div>
        <div class="bookmark-button" @click.stop="shareVerse">
          <FontAwesomeIcon icon="share-alt"/>
        </div>
        <div
            v-for="label of labels.filter(l => l.isRealLabel)"
            :key="label.id"
            :style="`color: ${adjustedColor(label.color)};`"
            class="bookmark-button"
            @click.stop="openStudyPad(label.id)"
        >
          <FontAwesomeIcon icon="file-alt"/>
        </div>

      </div>
      <div style="align-self: end;">
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

<script>
import LabelList from "@/components/LabelList";
import {inject} from "@vue/runtime-core";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Events, emit} from "@/eventbus";
import AreYouSure from "@/components/modals/AreYouSure";

export default {
  emits: ["selected"],
  name: "AmbiguousSelectionBookmarkButton",
  props: {
    bookmarkId: {required: true, type: Number},
  },
  components: {LabelList, FontAwesomeIcon, AreYouSure},
  setup(props, {emit: $emit}) {
    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const {adjustedColor, ...common} = useCommon();
    const bookmark = computed(() => {
      return bookmarkMap.get(props.bookmarkId);
    });

    const labels = computed(() => {
      return bookmark.value.labels.map(id => bookmarkLabels.get(id));
    });

    const primaryLabel = computed(() => {
      const primaryLabelId = bookmark.value.primaryLabelId || bookmark.value.labels[0];
      return bookmarkLabels.get(primaryLabelId);
    });

    const buttonStyle = computed(() => {
      return `background-color: ${adjustedColor(primaryLabel.value.color, -0.6)};`
    });

    const bookmarkColor = computed(() => {
      return `color: ${adjustedColor(primaryLabel.value.color)}`
    });

    const android = inject("android");

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
    }

    const areYouSure = ref(null);

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        android.removeBookmark(bookmark.value.id);
      }
    }

    function openStudyPad(labelId) {
        android.openStudyPad(labelId);
    }

    function editNotes() {
      $emit("selected");
      emit(Events.BOOKMARK_CLICKED, bookmark.value.id, {open: true});
    }

    function openBookmark() {
      $emit("selected");
      emit(Events.BOOKMARK_CLICKED, bookmark.value.id);
    }

    function bookmarkInfo() {
      $emit("selected");
      emit(Events.BOOKMARK_CLICKED, bookmark.value.id, {openInfo: true});
    }

    function shareVerse() {
      android.shareBookmarkVerse(bookmark.value.id);
    }

    return {
      bookmark, labels, buttonStyle, adjustedColor, bookmarkColor, assignLabels, editNotes,
      bookmarkInfo, shareVerse, removeBookmark, openStudyPad, areYouSure, openBookmark, ...common
    };
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.ambiguous-button {
  color: black;
  @extend .button;
  text-align: start;
}
</style>
