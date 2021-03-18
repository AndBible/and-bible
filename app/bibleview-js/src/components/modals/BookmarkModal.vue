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
  <Modal v-if="showBookmark" @close="closeBookmark">
    <EditableText
        v-if="!infoShown"
        constraint-display-height
        :text="bookmarkNotes || ''"
        @save="changeNote"
        show-placeholder
        max-editor-height="100pt"
    >
      {{ strings.editBookmarkPlaceholder }}
    </EditableText>
    <div v-show="infoShown" class="info">
      <div class="bible-text">
        <BookmarkText expanded :bookmark="bookmark"/>
      </div>
      <div v-if="bookmark.bookName">
        <span v-html="sprintf(strings.bookmarkAccurate, originalBookLink)"/>
      </div>
      <div v-else>
        <span v-html="sprintf(strings.bookmarkInaccurate, originalBookLink)"/>
      </div>
      {{ sprintf(strings.createdAt, formatTimestamp(bookmark.createdAt)) }}<br/>
      {{ sprintf(strings.lastUpdatedOn, formatTimestamp(bookmark.lastUpdatedOn)) }}<br/>
      <div class="links">
        <div>
          <a :href="`my-notes://?id=${bookmark.id}`">{{ strings.openMyNotes }}</a>
        </div>
        <div v-for="label in labels.filter(l => l.id > 0)" :key="label.id">
          <a :href="`journal://?id=${label.id}&bookmarkId=${bookmark.id}`">{{ sprintf(strings.openStudyPad, label.name) }}</a>
        </div>
      </div>
    </div>
    <template #title>
      <span :style="`color:${labelColor}`">
        <FontAwesomeIcon v-if="bookmarkNotes" icon="edit"/>
        <FontAwesomeIcon v-else icon="bookmark"/>
      </span>
      {{ bookmark.verseRangeAbbreviated }} <q v-if="bookmark.text"><i>{{ abbreviated(bookmark.text, 15)}}</i></q> <LabelList :bookmark="bookmark" :labels="labels"/>
    </template>
    <template #footer>
      <button class="button" @click="removeBookmark">{{strings.removeBookmark}}</button>
      <button class="button" @click="assignLabels">{{strings.assignLabels}}</button>
      <button :class="{'button': true, toggled: infoShown}" @click="infoShown = !infoShown">{{strings.bookmarkInfo}}</button>
      <button class="button right" @click="closeBookmark">{{strings.closeModal}}</button>
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
    const bookmarkNotes = ref(null);
    let originalNotes = null;

    setupEventBusListener(Events.BOOKMARK_FLAG_CLICKED, (bookmarkId_, {open = false} = {}) => {
      bookmarkId.value = bookmarkId_;
      bookmarkNotes.value = bookmark.value.notes;
      originalNotes = bookmark.value.notes;
      if(!showBookmark.value) infoShown.value = false;
      showBookmark.value = true;
    });

    function closeBookmark() {
      showBookmark.value = false;
      if(originalNotes !== bookmarkNotes.value)
        android.saveBookmarkNote(bookmark.value.id, bookmarkNotes.value);

      bookmarkNotes.value = null;
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
      bookmarkNotes.value = text;
      android.saveBookmarkNote(bookmark.value.id, bookmarkNotes.value);
    }

    const originalBookLink = computed(() =>
        `<a href="${bookmark.value.bibleUrl}">${bookmark.value.bookName || strings.defaultBook}</a>`)

    return {
      showBookmark, closeBookmark, areYouSure, infoShown, bookmarkNotes,
      removeBookmark,  assignLabels,  bookmark, labelColor, changeNote, labels, originalBookLink,
      strings, ...common
    };
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.info {
  @extend .visible-scrollbar;
  font-size: 80%;
  overflow-y: auto;

  max-height: calc(var(--max-height) - 25pt);
}
.links {
  padding-top: 10pt;
  padding-bottom: 5pt;

  font-size: 70%;
}
.bible-text {
  text-indent: 5pt;
  margin-bottom: 10pt;
  font-style: italic;
}

</style>
