<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <Modal v-if="showNote" @close="closeNote">
    <template v-if="editMode">
      <textarea
          @focusin="setFocus(true)"
          @focusout="setFocus(false)"
          :rows="bookmarkNoteRows"
          :placeholder="strings.editNotePlaceholder"
          class="edit-area"
          v-model="bookmark.notes"
      />
    </template>
    <template v-else>
      <p>{{bookmark.notes}}</p>
    </template>
    <div class="info" v-if="bookmark.book">
      {{ sprintf(strings.bookmarkAccurate, bookmark.book) }}
    </div>
    <template #title>
      {{ strings.bookmarkNote }}
      <FontAwesomeIcon v-if="bookmark.notes" @click="toggleEditMode" icon="edit"/>
    </template>
    <template #footer>
      <button class="button" @click="removeBookmark">{{strings.removeBookmark}}</button>
      <button class="button" @click="assignLabels">{{strings.assignLabels}}</button>
      <button class="button" @click="closeNote">{{strings.closeNote}}</button>
    </template>
  </Modal>
  <AreYouSure ref="areYouSure">
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
</template>

<script>
import Modal from "@/components/Modal";
import {Events, setupEventBusListener} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import AreYouSure from "@/components/AreYouSure";
export default {
  name: "NotesModal",
  components: {Modal, FontAwesomeIcon, AreYouSure},
  setup() {
    const showNote = ref(false);
    const editMode = ref(false);
    const android = inject("android");
    const bookmark = ref(null);
    const areYouSure = ref(null);

    setupEventBusListener(Events.NOTE_CLICKED, (b) => {
      showNote.value = true;
      bookmark.value = b;
      editMode.value = !b.notes;
    })

    function closeNote() {
      showNote.value = false;
      android.saveBookmarkNote(bookmark.value.id, bookmark.value.notes);
    }

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        showNote.value = false;
        android.removeBookmark(bookmark.value.id);
      }
    }

    function toggleEditMode(e) {
      editMode.value = !editMode.value;
      e.stopPropagation();
    }

    const bookmarkNoteRows = computed(() => {
      if(bookmark.value.notes === null) return 2;
      return Array.from(bookmark.value.notes.matchAll(/\n/g)).length + 2
    });

    function setFocus(value) {
      android.reportInputFocus(value);
    }

    return {
      setFocus, showNote, editMode, closeNote, areYouSure,
      toggleEditMode, removeBookmark,  assignLabels,  bookmark, bookmarkNoteRows,
      ...useCommon()
      };
  },
}
</script>

<style scoped>
.edit-button {
  width: 1em;
  height: 1em;
}
.edit-area {
  width: 100%;
}
.info {
  font-size: 80%;
}
</style>
