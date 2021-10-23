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
  <div class="entry" :class="{editMode}">
    <div class="menu" :class="{isText: journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT}">
      <ButtonRow show-drag-handle>
        <div class="journal-button" @click="addNewEntryAfter">
          <FontAwesomeIcon icon="plus-circle"/>
        </div>
        <div v-if="!journalText" class="journal-button" @click="editMode = true">
          <FontAwesomeIcon icon="edit"/>
        </div>

        <div v-if="journalEntry.indentLevel > 0" class="journal-button" @click.stop="indent(-1)">
          <FontAwesomeIcon icon="outdent"/>
        </div>

        <div v-if="journalEntry.indentLevel < 2" class="journal-button" @click.stop="indent(1)">
          <FontAwesomeIcon icon="indent"/>
        </div>

        <div v-if="journalEntry.type===StudyPadEntryTypes.BOOKMARK" class="journal-button" @click="changeExpanded(!journalEntry.expandContent)">
          <FontAwesomeIcon :icon="journalEntry.expandContent ? 'compress-arrows-alt' : 'expand-arrows-alt'"/>
        </div>

        <div class="journal-button" @click="deleteEntry">
          <FontAwesomeIcon icon="trash"/>
        </div>
        <div v-if="journalEntry.type===StudyPadEntryTypes.BOOKMARK" class="journal-button" @click.stop="editBookmark">
          <FontAwesomeIcon icon="info-circle"/>
        </div>
      </ButtonRow>
    </div>
    <template v-if="journalEntry.type===StudyPadEntryTypes.BOOKMARK">
      <b><a :href="bibleUrl">{{ journalEntry.bookInitials ? sprintf(strings.multiDocumentLink, journalEntry.verseRangeAbbreviated, journalEntry.bookAbbreviation ) : journalEntry.verseRangeAbbreviated }}</a></b>&nbsp;
      <BookmarkText :expanded="journalEntry.expandContent" :bookmark="journalEntry"/>
      <div v-if="(journalEntry.hasNote || editMode) && journalEntry.expandContent" class="note-separator"/>
    </template>
    <div :class="{'studypad-text-entry': journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT, notes: journalEntry.type === StudyPadEntryTypes.BOOKMARK}">
      <EditableText
        ref="editor"
        :show-placeholder="journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT"
        :edit-directly="journalEntry.new"
        :text="journalText"
        @opened="$emit('edit-opened')"
        @save="journalTextChanged"
      />
    </div>
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
import {StudyPadEntryTypes} from "@/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";
import {isBottomHalfClicked} from "@/utils";

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
    const editor = ref(null);

    const editMode = computed({
      get() {
        return editor.value && editor.value.editMode;
      },
      set(value) {
        editor.value.editMode = value;
      }
    });

    function journalTextChanged(newText) {
      if (props.journalEntry.type === StudyPadEntryTypes.BOOKMARK) {
        android.saveBookmarkNote(props.journalEntry.id, newText);
      } else if (props.journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT) {
        android.updateJournalEntry(props.journalEntry, {text: newText});
      }
    }

    const journalText = computed(() => {
      if (props.journalEntry.type === StudyPadEntryTypes.BOOKMARK) return props.journalEntry.notes;
      else if (props.journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT) return props.journalEntry.text;
    });

    function editBookmark(event) {
      ebEmit(Events.BOOKMARK_CLICKED, props.journalEntry.id, {openInfo: true, locateTop: isBottomHalfClicked(event)})
    }

    function addNewEntryAfter() {
      emit("add")
      android.createNewJournalEntry(props.label.id, props.journalEntry.type, props.journalEntry.id);
    }

    async function deleteEntry() {
      if (props.journalEntry.type === StudyPadEntryTypes.JOURNAL_TEXT) {
        const answer = await areYouSureDelete.value.areYouSure();
        if (answer) android.deleteJournalEntry(props.journalEntry.id);
      } else if (props.journalEntry.type === StudyPadEntryTypes.BOOKMARK) {
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
      () => {
        //const osis = props.journalEntry.wholeVerse
        //  ? props.journalEntry.osisRef
        //  : `${props.journalEntry.bookInitials}:${props.journalEntry.osisRef}`;
        const osis = props.journalEntry.osisRef;
        return `osis://?osis=${osis}&v11n=${props.journalEntry.v11n}`;
      }
    );

    return {
      bibleUrl,
      addNewEntryAfter,
      editBookmark,
      journalText,
      journalTextChanged,
      deleteEntry,
      areYouSureDelete,
      StudyPadEntryTypes,
      editor,
      editMode,
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
.entry {
  border-width: 2px;
}

.editMode {
  border-radius: 5px;
  border-style: solid;
  border-color: rgba(0, 0, 255, 0.5);
}
</style>
