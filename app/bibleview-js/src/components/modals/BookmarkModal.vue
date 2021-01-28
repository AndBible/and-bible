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
    <EditableText constraint-height :edit-directly="editDirectly" :text="bookmark.notes || ''" @changed="changeNote" max-height="inherit"/>
    <div v-if="bookmark.notes" class="my-notes-link">
      <a :href="`my-notes://?id=${bookmark.id}`">{{ strings.openMyNotes }}</a>
    </div>
    <div v-show="infoShown" class="info">
      <div v-if="bookmark.bookName">
        {{ sprintf(strings.bookmarkAccurate, bookmark.bookName) }}
      </div>
      {{ sprintf(strings.createdAt, formatTimestamp(bookmark.createdAt)) }}<br/>
      {{ sprintf(strings.lastUpdatedOn, formatTimestamp(bookmark.lastUpdatedOn)) }}<br/>
    </div>
    <template #title>
      <span :style="`color:${labelColor}`">
        <FontAwesomeIcon v-if="bookmark.notes" icon="edit"/>
        <FontAwesomeIcon v-else icon="bookmark"/>
      </span>
      {{ sprintf(strings.bookmarkTitle, bookmark.verseRange) }}
    </template>
    <template #footer>
      <button class="button" @click="removeBookmark">{{strings.removeBookmark}}</button>
      <button class="button" @click="assignLabels">{{strings.assignLabels}}</button>
      <button class="button" @click="infoShown = !infoShown">{{strings.bookmarkInfo}}</button>
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
import Color from "color";
import EditableText from "@/components/EditableText";
import {debounce} from "lodash";

export default {
  name: "BookmarkModal",
  components: {EditableText, Modal, FontAwesomeIcon, AreYouSure},
  setup() {
    const showBookmark = ref(false);
    const android = inject("android");
    const bookmark = ref(null);
    const areYouSure = ref(null);
    const infoShown = ref(false);
    const label = ref({});
    const editDirectly = ref(false);
    setupEventBusListener(Events.BOOKMARK_FLAG_CLICKED, (b, labels, {open = false} = {}) => {
      editDirectly.value = open || !b.notes;
      showBookmark.value = true;
      bookmark.value = b;
      label.value = labels[0];
    })

    function closeBookmark() {
      showBookmark.value = false;
      android.saveBookmarkNote(bookmark.value.id, bookmark.value.notes);
    }

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
      showBookmark.value = false;
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        showBookmark.value = false;
        android.removeBookmark(bookmark.value.id);
      }
    }

    const bookmarkComputed = computed(() => {
      if(bookmark.value) return bookmark.value;
      return {
        id: null,
        notes: null,
      }
    });

    const labelColor = computed(() => {
        return Color(label.value.color).darken(0.2).hsl().string();
    });

    const changeNote = debounce((text) => {
      bookmark.value.notes = text;
    }, 500)

    return {
      showBookmark, closeBookmark, areYouSure, infoShown, editDirectly,
      removeBookmark,  assignLabels,  bookmark: bookmarkComputed, labelColor, changeNote,
      ...useCommon()
    };
  },
}
</script>

<style scoped lang="scss">
.info {
  background: rgba(0,0,0,0.1);
  .night & {
    background: rgba(255,255,255,0.1);

  }
  margin-top: 10pt;
  font-size: 80%;
}
.my-notes-link {
  padding-top: 10pt;
  padding-bottom: 5pt;

  font-size: smaller;
}

</style>
