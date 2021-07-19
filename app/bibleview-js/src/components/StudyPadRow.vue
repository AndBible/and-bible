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
      {{ strings.removeStudyPadConfirmationTitle }}
    </template>
    {{ strings.doYouWantToDeleteEntry }}
  </AreYouSure>
  <div class="menu">
    <ButtonRow show-drag-handle>
      <div class="journal-button" @click="addNewEntryAfter">
        <FontAwesomeIcon icon="plus-circle"/>
      </div>
      <div v-if="!journalText" class="journal-button" @click="editor.editMode = true">
        <FontAwesomeIcon icon="edit"/>
      </div>

      <div v-if="journalEntry.indentLevel > 0" class="journal-button" @click.stop="indent(-1)">
        <FontAwesomeIcon icon="outdent"/>
      </div>

      <div v-if="journalEntry.indentLevel < 2" class="journal-button" @click.stop="indent(1)">
        <FontAwesomeIcon icon="indent"/>
      </div>

      <div class="journal-button" @click="deleteEntry">
        <FontAwesomeIcon icon="trash"/>
      </div>
      <div v-if="journalEntry.type===JournalEntryTypes.BOOKMARK" class="journal-button" @click.stop="editBookmark">
        <FontAwesomeIcon icon="bookmark"/>
      </div>
    </ButtonRow>
  </div>
  <template v-if="journalEntry.type===JournalEntryTypes.BOOKMARK">
    <b><a :href="bibleUrl">{{ journalEntry.bookInitials ? sprintf(strings.multiDocumentLink, journalEntry.verseRangeAbbreviated, journalEntry.bookAbbreviation ) : journalEntry.verseRangeAbbreviated }}</a></b>&nbsp;
    <BookmarkText :expanded="journalEntry.expandContent" @change-expanded="changeExpanded" :bookmark="journalEntry"/>
    <div v-if="journalEntry.hasNote && journalEntry.expandContent" class="separator"/>
  </template>
  <div :class="{'studypad-text-entry': journalEntry.type === JournalEntryTypes.JOURNAL_TEXT, notes: journalEntry.type === JournalEntryTypes.BOOKMARK}">
    <EditableText
      ref="editor"
      :show-placeholder="journalEntry.type === JournalEntryTypes.JOURNAL_TEXT"
      :edit-directly="journalEntry.new"
      :text="journalText"
      @opened="$emit('edit-opened')"
      @save="journalTextChanged"
    />
  </div>
</template>

<script>
import BookmarkText from "@/components/BookmarkText";
import EditableText from "@/components/EditableText";
import ButtonRow from "@/components/ButtonRow";
import {emit as ebEmit, Events} from "@/eventbus";
import {inject} from "@vue/runtime-core";
import AreYouSure from "@/components/modals/AreYouSure";
import {computed, ref} from "@vue/reactivity";
import {JournalEntryTypes} from "@/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";

export default {
  name: "StudyPadRow",
  components: {ButtonRow, EditableText, BookmarkText, AreYouSure, FontAwesomeIcon},
  emits: ['edit-opened', 'add'],
  props: {
    journalEntry: {type: Object, required:true},
    label: {type: Object, required:true}
  },
  setup: function (props, {emit}) {
    const android = inject("android");
    const areYouSureDelete = ref(null);
    const {strings, ...common} = useCommon();

    function journalTextChanged(newText) {
      if (props.journalEntry.type === JournalEntryTypes.BOOKMARK) {
        android.saveBookmarkNote(props.journalEntry.id, newText);
      } else if (props.journalEntry.type === JournalEntryTypes.JOURNAL_TEXT) {
        android.updateJournalEntry(props.journalEntry, {text: newText});
      }
    }

    const journalText = computed(() => {
      if (props.journalEntry.type === JournalEntryTypes.BOOKMARK) return props.journalEntry.notes;
      else if (props.journalEntry.type === JournalEntryTypes.JOURNAL_TEXT) return props.journalEntry.text;
    });

    function editBookmark() {
      ebEmit(Events.BOOKMARK_CLICKED, props.journalEntry.id)
    }

    function addNewEntryAfter() {
      emit("add")
      android.createNewJournalEntry(props.label.id, props.journalEntry.type, props.journalEntry.id);
    }

    async function deleteEntry() {
      if (props.journalEntry.type === JournalEntryTypes.JOURNAL_TEXT) {
        const answer = await areYouSureDelete.value.areYouSure();
        if (answer) android.deleteJournalEntry(props.journalEntry.id);
      } else if (props.journalEntry.type === JournalEntryTypes.BOOKMARK) {
        let answer;
        if (props.journalEntry.labels.length > 1) {
          const buttons = [{
            title: strings.onlyLabel,
            result: "only_label",
            class: "warning",
          }, {
            title: strings.wholeBookmark,
            result: "bookmark",
            class: "warning",
          }];
          answer = await areYouSureDelete.value.areYouSure(buttons);
        } else if (await areYouSureDelete.value.areYouSure()) {
          answer = "bookmark"
        }
        if (answer === "only_label") {
          android.removeBookmarkLabel(props.journalEntry.id, props.label.id);
        } else if (answer === "bookmark") {
          android.removeBookmark(props.journalEntry.id);
        }
      }
    }

    function indent(change) {
      android.updateJournalEntry(props.journalEntry, {indentLevel: props.journalEntry.indentLevel + change})
    }

    function changeExpanded(newValue) {
      android.updateJournalEntry(props.journalEntry, {expandContent: newValue})
    }

    const bibleUrl = computed(
      () => `osis://?osis=${props.journalEntry.osisRef}&v11n=${props.journalEntry.v11n}`
    );

    return {
      bibleUrl,
      addNewEntryAfter,
      editBookmark,
      journalText,
      journalTextChanged,
      deleteEntry,
      areYouSureDelete,
      JournalEntryTypes,
      editor: ref(null),
      strings,
      indent,
      changeExpanded,
      ...common
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.notes {
  text-indent: 2pt;
  margin-top: 4pt;
}
</style>
