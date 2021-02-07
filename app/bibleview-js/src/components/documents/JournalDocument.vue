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
  <h2>{{ document.label.name }}</h2>
  <div v-if="journalEntries.length === 0">
    {{strings.emptyJournal}}
  </div>
  <div v-for="j in journalEntries" :id="`${j.type}-${j.id}`" :key="`${j.type}-${j.id}`">
    <JournalRow
        :journal-entry="j"
        :label="document.label"
        @add="adding=true"
        @edit-opened="editOpened(j)"
    />
  </div>
</template>

<script>
import {computed, ref} from "@vue/reactivity";
import {inject, reactive, provide, nextTick} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {Events, setupEventBusListener} from "@/eventbus";
import {sortBy} from "lodash";
import JournalRow from "@/components/JournalRow";

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
  components: {JournalRow},
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

    globalBookmarks.updateBookmarks(...bookmarks);

    const journalEntries = computed(() => {
      let entries = [];
      entries.push(...globalBookmarks.bookmarks.value.filter(b => b.labels.includes(label.id)))
      entries.push(...journalTextEntries.values())
      entries = sortBy(entries, ['orderNumber']);
      return entries;
    });
    const adding = ref(false);

    setupEventBusListener(Events.ADD_OR_UPDATE_JOURNAL, async ({journal, bookmarkToLabelsOrdered, journalsOrdered}) => {
      if(journal && adding.value) {
        journal.new = true
        adding.value = false;
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

    const editableJournalEntry = ref(null);

    function labelsFor(b) {
      return b.labels.map(l => globalBookmarks.bookmarkLabels.get(l));
    }

    async function editOpened(entry) {
      await nextTick();
      scrollToId(`${entry.type}-${entry.id}`, {duration: 300})
    }

    return {
      journalEntries, editNotes, adding,
      labelsFor, editableJournalEntry,  editOpened,
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
</style>

<style>
.highlight {
  font-weight: bold;
}
</style>
