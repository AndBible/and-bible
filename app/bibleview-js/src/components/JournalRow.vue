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
  <AreYouSure ref="areYouSureDelete">
    <template #title>
      {{ strings.removeJournalConfirmationTitle }}
    </template>
    {{ strings.doYouWantToDeleteEntry }}
  </AreYouSure>
  <div class="note-container">
    <JournalEditButtons>
      <div v-if="journalEntry.type===JournalEntryTypes.BOOKMARK" class="journal-button" @click="editBookmark">
        <FontAwesomeIcon icon="bookmark"/>
      </div>
      <div class="journal-button" @click="addNewEntryAfter">
        <FontAwesomeIcon icon="plus-circle"/>
      </div>
      <div class="journal-button" @click="editEntry">
        <FontAwesomeIcon icon="edit"/>
      </div>
      <div class="journal-button" @click="deleteEntry">
        <FontAwesomeIcon icon="trash"/>
      </div>
    </JournalEditButtons>
    <template v-if="journalEntry.type===JournalEntryTypes.BOOKMARK">
      <b><a :href="journalEntry.bibleUrl">{{ journalEntry.verseRangeAbbreviated }}</a></b> <BookmarkText :bookmark="journalEntry"/>
    </template>
    <div class="notes">
      <EditableText
          :show-placeholder="journalEntry.type === JournalEntryTypes.JOURNAL_TEXT"
          :edit-directly="journalEntry.new"
          :text="journalText"
          @opened="$emit('edit-opened')"
          @closed="journalTextChanged"
      />
    </div>
  </div>
</template>

<script>
import BookmarkText from "@/components/BookmarkText";
import EditableText from "@/components/EditableText";
import JournalEditButtons from "@/components/JournalEditButtons";
import {emit as ebEmit, Events} from "@/eventbus";
import {inject} from "@vue/runtime-core";
import AreYouSure from "@/components/modals/AreYouSure";
import {computed, ref} from "@vue/reactivity";
import {JournalEntryTypes} from "@/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";

export default {
  name: "JournalRow",
  components: {JournalEditButtons, EditableText, BookmarkText, AreYouSure, FontAwesomeIcon},
  emits: ['edit-opened', 'add'],
  props: {
    journalEntry: {type: Object, required:true},
    label: {type: Object, required:true}
  },
  setup(props, {emit}) {
    const android = inject("android");
    const areYouSureDelete = ref(null);

    function journalTextChanged(entry, newText) {
      if(entry.type === JournalEntryTypes.BOOKMARK) {
        entry.notes = newText;
      } else if(entry.type === JournalEntryTypes.JOURNAL_TEXT) {
        entry.text = newText;
        android.updateJournalTextEntry(entry);
      }
    }
    const journalText = computed(() => {
      if(props.journalEntry.type === JournalEntryTypes.BOOKMARK) return props.journalEntry.notes;
      else if(props.journalEntry.type === JournalEntryTypes.JOURNAL_TEXT) return props.journalEntry.text;
    });

    function editBookmark(bookmark) {
      ebEmit(Events.BOOKMARK_FLAG_CLICKED, bookmark.id, {open: true})
    }

    function addNewEntryAfter() {
      emit("add")
      android.createNewJournalEntry(props.label.id, props.journalEntry.type, props.journalEntry.id);
    }

    async function deleteEntry() {
      const answer = await areYouSureDelete.value.areYouSure();
      if (answer) {
        if (props.journalEntry.type === JournalEntryTypes.JOURNAL_TEXT)
          android.deleteJournalEntry(props.journalEntry.id);
        else if (props.journalEntry.type === JournalEntryTypes.BOOKMARK) {
          android.removeBookmarkLabel(props.journalEntry.id, props.label.id);
        }
      }
    }

    function editEntry() {

    }

    return {addNewEntryAfter, editBookmark, journalText,
      journalTextChanged, deleteEntry, editEntry, areYouSureDelete, JournalEntryTypes, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

</style>
