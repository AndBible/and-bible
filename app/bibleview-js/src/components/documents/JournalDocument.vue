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
  <h2>{{ document.label.name }}</h2>
  <div v-if="journalEntries.length === 0">
    {{strings.emptyJournal}}
  </div>
  <div v-for="j in journalEntries" :id="`${j.type}-${j.id}`" :key="`${j.type}-${j.id}`">
    <div class="note-container">
      <div class="edit-buttons">
        <div v-if="j.type===JournalEntryTypes.BOOKMARK" class="edit-button" @click.stop="editBookmark(j)">
          <FontAwesomeIcon icon="bookmark"/>
        </div>
        <div class="add-entry" @click.stop="addNewEntryAfter(j)">
          <FontAwesomeIcon icon="plus-circle"/>
        </div>
        <div class="delete-button" @click.stop="deleteEntry(j)">
          <FontAwesomeIcon icon="trash"/>
        </div>
      </div>
      <template v-if="j.type===JournalEntryTypes.BOOKMARK">
        <b><a :href="j.bibleUrl">{{ j.verseRangeAbbreviated }}</a></b> <BookmarkText :bookmark="j"/>
      </template>
      <div class="notes">
        <EditableText
            :edit-directly="j.new"
            :text="journalText(j)"
            @opened="editOpened(j)"
            @closed="journalTextChanged(j, $event)"
        />
      </div>
    </div>
  </div>
</template>

<script>
import {computed, ref} from "@vue/reactivity";
import {inject, reactive, provide, nextTick} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {emit, Events, setupEventBusListener} from "@/eventbus";
import {sortBy} from "lodash";
import EditableText from "@/components/EditableText";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import AreYouSure from "@/components/modals/AreYouSure";
import BookmarkText from "@/components/BookmarkText";

const JournalEntryTypes = {
  BOOKMARK: "bookmark",
  JOURNAL_TEXT: "journal",
}

function useJournal(label) {
  const journalTextEntries = reactive(new Map());

  function updateJournalTextEntries(...entries) {
    for(const e of entries)
      if(e.labelId === label.id)
        journalTextEntries.set(e.id, e);
  }

  function updateJournalOrdering(...entries) {
    for(const e of entries) {
      journalTextEntries.get(e.id).orderNumber = e.orderNumber;
    }
  }
  function deleteJournal(journalId) {
    journalTextEntries.delete(journalId)
  }
  return {journalTextEntries, updateJournalTextEntries, updateJournalOrdering, deleteJournal};
}

export default {
  name: "JournalDocument",
  components: {AreYouSure, EditableText, FontAwesomeIcon, BookmarkText},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {bookmarks, label} = props.document;
    const journal = useJournal(label);
    provide("journal", journal);
    const {scrollToId} = inject("scroll");

    const {journalTextEntries, updateJournalTextEntries, updateJournalOrdering, deleteJournal} = journal;

    updateJournalTextEntries(...props.document.journalTextEntries);

    const globalBookmarks = inject("globalBookmarks");
    const android = inject("android");

    globalBookmarks.updateBookmarks(...bookmarks);

    const journalEntries = computed(() => {
      let entries = [];
      entries.push(...globalBookmarks.bookmarks.value.filter(b => b.labels.includes(label.id)))
      entries.push(...journalTextEntries.values())
      entries = sortBy(entries, ['orderNumber']);
      return entries;
    });
    let adding = false;

    setupEventBusListener(Events.ADD_OR_UPDATE_JOURNAL, async ({journal, bookmarkToLabelsOrdered, journalsOrdered}) => {
      if(journal && adding) {
        journal.new = true
        adding = false;
      }
      globalBookmarks.updateBookmarkOrdering(...bookmarkToLabelsOrdered);
      updateJournalOrdering(...journalsOrdered);
      if(journal) {
        updateJournalTextEntries(journal);
      }
      await nextTick();
      if(journal && journal.new) {
        scrollToId(`${journal.type}-${journal.id}`, {duration: 300})
      }
    })

    setupEventBusListener(Events.DELETE_JOURNAL, journalId => {
      deleteJournal(journalId)
    })

    function editNotes(b, newText) {
      b.notes = newText;
    }

    function save(b) {
      android.saveBookmarkNote(b.id, b.notes);
    }
    const editableJournalEntry = ref(null);

    function editBookmark(bookmark) {
      emit(Events.BOOKMARK_FLAG_CLICKED, bookmark.id, {open: true})
    }

    function labelsFor(b) {
      return b.labels.map(l => globalBookmarks.bookmarkLabels.get(l));
    }

    function assignLabels(bookmark) {
      android.assignLabels(bookmark.id);
    }

    function journalTextChanged(entry, newText) {
      if(entry.type === JournalEntryTypes.BOOKMARK) {
        entry.notes = newText;
      } else if(entry.type === JournalEntryTypes.JOURNAL_TEXT) {
        entry.text = newText;
        android.updateJournalTextEntry(entry);
      }
    }
    function journalText(entry) {
      if(entry.type === JournalEntryTypes.BOOKMARK) return entry.notes;
      else if(entry.type === JournalEntryTypes.JOURNAL_TEXT) return entry.text;
    }

    function addNewEntryAfter(entry) {
      adding = true;
      android.createNewJournalEntry(label.id, entry.type, entry.id);
    }

    const areYouSureDelete = ref(null);

    async function deleteEntry(entry) {
      const answer = await areYouSureDelete.value.areYouSure();
      if (answer) {
        if (entry.type === JournalEntryTypes.JOURNAL_TEXT)
          android.deleteJournalEntry(entry.id);
        else if (entry.type === JournalEntryTypes.BOOKMARK) {
          android.removeBookmarkLabel(entry.id, label.id);
        }
      }
    }

    async function editOpened(entry) {
      await nextTick();
      scrollToId(`${entry.type}-${entry.id}`, {duration: 300})
    }

    return {
      journalEntries, journalText, journalTextChanged, save, editNotes,
      editBookmark, labelsFor, assignLabels, editableJournalEntry,
      addNewEntryAfter, deleteEntry, JournalEntryTypes, areYouSureDelete, editOpened,
      ...useCommon()
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.bible-text {
  margin-top: 2pt;
  text-indent: 5pt;
  margin-bottom: 2pt;
  font-style: italic;
}

.notes {
  text-indent: 2pt;
  margin-top: 4pt;
}
.edit-buttons {
  position: absolute;
  right: 0;
  display: flex;
  justify-content: flex-end;
}
.buttons {
  @extend .journal-button;
  margin: 2pt;
}
.edit-button {
  @extend .buttons;
}

.delete-button {
  @extend .buttons;
}

.add-entry {
  @extend .buttons;
}
</style>

<style>
.highlight {
  font-weight: bold;
}
</style>
