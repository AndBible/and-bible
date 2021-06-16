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
  <Modal v-if="showBookmark && bookmark" @close="closeBookmark" wide>
    <template #title-div>
      <div class="bookmark-title" style="width: calc(100% - 80px);">
        <div class="overlay"/>
        <div style="overflow-x: auto">
          <LabelList single-line handle-touch in-bookmark :bookmark-id="bookmark.id"/>
        </div>
        <div class="title-text">
          {{ bookmark.verseRangeAbbreviated }} <q v-if="bookmark.text"><i>{{ abbreviated(bookmark.text, 25)}}</i></q>
        </div>
      </div>
    </template>

    <template #buttons>
      <div class="modal-toolbar">
        <div class="modal-action-button" :class="{toggled: !infoShown}" @click="toggleInfo" @touchstart="toggleInfo">
          <FontAwesomeIcon icon="edit"/>
        </div>
        <div class="modal-action-button right" @touchstart.stop @click.stop="closeBookmark">
          <FontAwesomeIcon icon="times"/>
        </div>
      </div>
    </template>

    <EditableText
      v-if="!infoShown"
      constraint-display-height
      :text="bookmarkNotes || ''"
      @save="changeNote"
      show-placeholder
      :edit-directly="editDirectly"
    >
      {{ strings.editBookmarkPlaceholder }}
    </EditableText>
    <div v-if="infoShown" class="info">
      <BookmarkButtons
        :bookmark="bookmark"
        in-bookmark-modal
        @close-bookmark="showBookmark = false"
      />
      <div class="bible-text">
        <BookmarkText expanded :bookmark="bookmark"/>
      </div>
      <div class="links">
        <div class="link-line">
          <span class="link-icon"><FontAwesomeIcon icon="file-alt"/></span>
          <a :href="`my-notes://?id=${bookmark.id}`">{{ strings.openMyNotes }}</a>
        </div>
        <div v-for="label in labels" :key="`label-${bookmark.id}-${label.id}`" class="link-line">
          <span class="link-icon" :style="`color: ${adjustedColor(label.color).string()};`"><FontAwesomeIcon icon="file-alt"/></span>
          <a :href="`journal://?id=${label.id}&bookmarkId=${bookmark.id}`">{{ sprintf(strings.openStudyPad, label.name) }}</a>
        </div>
      </div>
      <div class="info-text">
        <div v-if="bookmark.bookName">
          <span v-html="sprintf(strings.bookmarkAccurate, originalBookLink)"/>
        </div>
        <div v-else>
          <span v-html="sprintf(strings.bookmarkInaccurate, originalBookLink)"/>
        </div>
        {{ sprintf(strings.createdAt, formatTimestamp(bookmark.createdAt)) }}<br/>
        {{ sprintf(strings.lastUpdatedOn, formatTimestamp(bookmark.lastUpdatedOn)) }}<br/>
      </div>
    </div>
    <template #footer>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {Events, setupEventBusListener} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import EditableText from "@/components/EditableText";
import LabelList from "@/components/LabelList";
import BookmarkText from "@/components/BookmarkText";
import BookmarkButtons from "@/components/BookmarkButtons";
import {clickWaiter} from "@/utils";

export default {
  name: "BookmarkModal",
  components: {BookmarkText, LabelList, EditableText, Modal, FontAwesomeIcon, BookmarkButtons},
  setup() {
    const showBookmark = ref(false);
    const android = inject("android");
    const areYouSure = ref(null);
    const infoShown = ref(false);
    const bookmarkId = ref(null);

    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");

    const bookmark = computed(() => {
      return bookmarkMap.get(bookmarkId.value);
    });

    const labels = computed(() => {
      if(!bookmark.value) return [];
      return bookmark.value.labels.map(l => bookmarkLabels.get(l))
    });

    const label = computed(() => labels.value[0]);
    const bookmarkNotes = computed(() => bookmark.value.notes);
    let originalNotes = null;

    setupEventBusListener(Events.BOOKMARK_CLICKED, (bookmarkId_, {openInfo = false, open = false} = {}) => {
      bookmarkId.value = bookmarkId_;
      originalNotes = bookmarkNotes.value;
      //if(!showBookmark.value) infoShown.value = false;
      infoShown.value = openInfo || !bookmarkNotes.value;
      editDirectly.value = open;
      showBookmark.value = true;
    });

    function closeBookmark() {
      showBookmark.value = false;
      if(originalNotes !== bookmarkNotes.value)
        android.saveBookmarkNote(bookmark.value.id, bookmarkNotes.value);

      originalNotes = null;
    }

    const {adjustedColor, strings, ...common} = useCommon();

    const labelColor = computed(() => {
      return adjustedColor(label.value.color).string();
    });

    const changeNote = text => {
      android.saveBookmarkNote(bookmark.value.id, text);
    }

    const originalBookLink = computed(() =>
      `<a href="${bookmark.value.bibleUrl}">${bookmark.value.bookName || strings.defaultBook}</a>`)

    const editDirectly = ref(false);

    const {waitForClick} = clickWaiter();

    async function toggleInfo(event) {
      if(!await waitForClick(event)) return;
      infoShown.value = !infoShown.value
      if(!infoShown.value && !bookmarkNotes.value) {
        editDirectly.value = true;
      }
    }

    return {
      showBookmark, closeBookmark, areYouSure, infoShown, bookmarkNotes,  bookmark, labelColor,
      changeNote, labels, originalBookLink, strings, adjustedColor, editDirectly, toggleInfo, ...common
    };
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.bookmark-title {
  padding-top: 2px;
}

.title-text {
  font-weight: normal;
  padding-top: 2px;
}


.modal-toolbar {
  align-self: flex-end;
  display: flex;
}

.link-icon {
  padding: 2px;
  padding-inline-end: 4px;
  color: $button-grey;
}

.info-text {
font-size: 85%;
}

.info {
  @extend .visible-scrollbar;
  font-size: 90%;
  overflow-y: auto;

  max-height: calc(var(--max-height) - 25pt);
}
.links {
  padding-top: 10pt;
  padding-bottom: 5pt;
  .link-line {
    padding: 4px;
  }
}
//.action-buttons {
//  position: relative;
//  right: 0;
//}
.bible-text {
  text-indent: 5pt;
  font-style: italic;
}
.overlay {
  position: absolute;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0), $modal-header-background-color);
  .night & {
    background: linear-gradient(90deg, rgba(0, 0, 0, 0), $night-modal-header-background-color);
  }
  right: 80px; top: 0;
  width: 30px;
  height: 2em;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #c1c1c1
}
</style>
