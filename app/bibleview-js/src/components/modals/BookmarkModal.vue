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
  <Modal v-if="showBookmark && bookmark" @close="closeBookmark">
    <template #title-div>
      <div class="bookmark-title">
        <div>
          <LabelList handle-touch :bookmark-id="bookmark.id"/>
        </div>
        <div class="title-text">
          {{ bookmark.verseRangeAbbreviated }} <q v-if="bookmark.text"><i>{{ abbreviated(bookmark.text, 15)}}</i></q>
        </div>
      </div>
    </template>

    <template #buttons>
      <div class="modal-toolbar">
        <div class="modal-action-button" :class="{toggled: infoShown}" @touchstart.stop="infoShown = !infoShown">
          <FontAwesomeIcon icon="info-circle"/>
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
      max-editor-height="100pt"
    >
      {{ strings.editBookmarkPlaceholder }}
    </EditableText>
    <div v-show="infoShown" class="info">
      <div class="bible-text">
        <BookmarkText expanded :bookmark="bookmark"/>
      </div>
      <div class="links">
        <div class="link-line">
          <span class="link-icon"><FontAwesomeIcon icon="file-alt"/></span>
          <a :href="`my-notes://?id=${bookmark.id}`">{{ strings.openMyNotes }}</a>
        </div>
        <div v-for="label in labels.filter(l => l.id > 0)" :key="label.id" class="link-line">
          <span class="link-icon" :style="`color: ${adjustedColor(label.color).string()};`"><FontAwesomeIcon icon="file-alt"/></span>
          <a :href="`journal://?id=${label.id}&bookmarkId=${bookmark.id}`">{{ sprintf(strings.openStudyPad, label.name) }}</a>
        </div>
      </div>

      <div style="display: flex; justify-content: space-between;">
        <div class="bookmark-buttons">
          <div class="bookmark-button" @click="assignLabels">
            <FontAwesomeIcon icon="tags"/>
          </div>
          <div class="bookmark-button" @click="infoShown=false">
            <FontAwesomeIcon icon="edit"/>
          </div>
          <div class="bookmark-button" @click="shareVerse">
            <FontAwesomeIcon icon="share-alt"/>
          </div>
        </div>
        <div class="bookmark-buttons" style="align-self: end;">
          <div class="bookmark-button end" @click="removeBookmark">
            <FontAwesomeIcon icon="trash"/>
          </div>
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
  <AreYouSure ref="areYouSure">
    <template #title>
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {Events, setupEventBusListener} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import AreYouSure from "@/components/modals/AreYouSure";
import EditableText from "@/components/EditableText";
import LabelList from "@/components/LabelList";
import BookmarkText from "@/components/BookmarkText";

export default {
  name: "BookmarkModal",
  components: {BookmarkText, LabelList, EditableText, Modal, FontAwesomeIcon, AreYouSure},
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
      infoShown.value = openInfo;
      editDirectly.value = open;
      showBookmark.value = true;
    });

    function closeBookmark() {
      showBookmark.value = false;
      if(originalNotes !== bookmarkNotes.value)
        android.saveBookmarkNote(bookmark.value.id, bookmarkNotes.value);

      originalNotes = null;
    }

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        showBookmark.value = false;
        android.removeBookmark(bookmark.value.id);
      }
    }

    const {adjustedColor, strings, ...common} = useCommon();

    const labelColor = computed(() => {
      return adjustedColor(label.value.color).string();
    });

    const changeNote = text => {
      android.saveBookmarkNote(bookmark.value.id, text);
    }

    function shareVerse() {
      android.shareBookmarkVerse(bookmark.value.id);
    }

    const originalBookLink = computed(() =>
      `<a href="${bookmark.value.bibleUrl}">${bookmark.value.bookName || strings.defaultBook}</a>`)

    const editDirectly = ref(false);

    return {
      showBookmark, closeBookmark, areYouSure, infoShown, bookmarkNotes, shareVerse,
      removeBookmark,  assignLabels,  bookmark, labelColor, changeNote, labels, originalBookLink,
      strings, adjustedColor, editDirectly, ...common
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
    padding: 2px;
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

</style>
