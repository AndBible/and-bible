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
  <div class="journal-name">
    <span :style="labelNameStyle">{{ document.label.name }}</span>
  </div>
  <div v-if="journalEntries.length === 0">
    {{strings.emptyStudyPad}}
    <span class="journal-button" @click="addNewEntry">
      <FontAwesomeIcon icon="plus-circle"/>
    </span>
  </div>
  <draggable
    v-model="journalEntries"
    handle=".drag-handle"
    group="journal-entries"
    ghost-class="drag-ghost"
    chosen-class="drag-chosen"
    :item-key="(e) => `${e.type}-${e.id}`"
  >
    <template #item="{element: j}">
      <div class="studypad-container" :style="indentStyle(j)" :id="`${j.type}-${j.id}`">
        <StudyPadRow
          :journal-entry="j"
          :label="document.label"
          @add="adding=true"
        />
      </div>
    </template>
  </draggable>
</template>

<script>
import {computed, ref} from "@vue/reactivity";
import {inject, provide, nextTick} from "@vue/runtime-core";
import {useCommon, useJournal} from "@/composables";
import {Events, setupEventBusListener} from "@/eventbus";
import {groupBy, sortBy} from "lodash";
import StudyPadRow from "@/components/StudyPadRow";
import draggable from "vuedraggable";
import {JournalEntryTypes} from "@/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {adjustedColorOrig} from "@/utils";

export default {
  name: "StudyPadDocument",
  components: {StudyPadRow, draggable, FontAwesomeIcon},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {bookmarks, label, journalTextEntries: journalTextEntries_, bookmarkToLabels: bookmarkToLabels_} = props.document;
    const journal = useJournal(label);
    provide("journal", journal);
    const {scrollToId} = inject("scroll");
    const android = inject("android");

    const {
      journalTextEntries, updateBookmarkToLabels, updateJournalTextEntries,
      updateJournalOrdering, deleteJournal, bookmarkToLabels} = journal;

    updateJournalTextEntries(...journalTextEntries_);
    updateBookmarkToLabels(...bookmarkToLabels_)

    const globalBookmarks = inject("globalBookmarks");

    globalBookmarks.updateBookmarks(...bookmarks);

    const journalEntries = computed({
      get:
        () => {
          let entries = [];
          entries.push(...globalBookmarks.bookmarks.value.filter(b => b.labels.includes(label.id)).map(e => {
            const bookmarkToLabel = bookmarkToLabels.get(e.id);
            return {
              ...e,
              orderNumber: bookmarkToLabel.orderNumber,
              indentLevel: bookmarkToLabel.indentLevel,
              expandContent: bookmarkToLabel.expandContent,
              bookmarkToLabel
            }
          }))
          entries.push(...journalTextEntries.values())
          entries = sortBy(entries, ['orderNumber']);
          return entries;
        },
      set(values) {
        let count = 0;
        const changed = [];
        for(const v of values) {
          const newOrder = count++
          if(v.orderNumber !== newOrder) {
            changed.push(v);
          }
          if(v.type === JournalEntryTypes.BOOKMARK) {
            v.bookmarkToLabel.orderNumber = newOrder;
          }
          v.orderNumber = newOrder;

        }
        const grouped = groupBy(changed, "type");
        const bookmarks = grouped[JournalEntryTypes.BOOKMARK] || [];
        const journals = grouped[JournalEntryTypes.JOURNAL_TEXT] || [];
        android.updateOrderNumber(label.id, bookmarks, journals);
      }
    });
    const adding = ref(false);

    setupEventBusListener(Events.ADD_OR_UPDATE_JOURNAL, async ({journal, bookmarkToLabelsOrdered, journalsOrdered}) => {
      if(journal && adding.value) {
        journal.new = true
        adding.value = false;
      }
      updateBookmarkToLabels(...bookmarkToLabelsOrdered);
      updateJournalOrdering(...journalsOrdered);
      if(journal) {
        updateJournalTextEntries(journal);
      }
      await nextTick();
      if(journal && journal.new) {
        scrollToId(`${journal.type}-${journal.id}`, {duration: 300})
      }
    })

    setupEventBusListener(Events.ADD_OR_UPDATE_BOOKMARK_TO_LABEL, bookmarkToLabel => {
      updateBookmarkToLabels(bookmarkToLabel);
    })

    setupEventBusListener(Events.DELETE_JOURNAL, journalId => {
      deleteJournal(journalId)
    })

    function editNotes(b, newText) {
      b.notes = newText;
    }

    const editableJournalEntry = ref(null);

    function indentStyle(entry) {
      const margin = entry.indentLevel * 20;
      return `margin-left: ${margin}px;`;
    }

    function addNewEntry() {
      android.createNewJournalEntry(label.id);
    }

    const appSettings = inject("appSettings");

    const labelNameStyle = computed(() => {
      const color = adjustedColorOrig(label.style.color).alpha(appSettings.nightMode ? 0.8: 0.3).hsl().string();
      return `background-color: ${color};`;
    });

    return {
      journalEntries, editNotes, adding, indentStyle,
      editableJournalEntry,  addNewEntry, labelNameStyle,
      ...useCommon()
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

div.journal-name {
  padding-top: 15px;
  padding-bottom: 15px;
  span {
    font-size: 180%;
    border-radius: 10px;
    padding: 5px;
    margin-inline-start: 15px;
    font-weight: bold;
  }
}

.studypad-container {
  @extend .note-container;
}

.bible-text {
  margin-top: 2pt;
  text-indent: 5pt;
  margin-bottom: 2pt;
  font-style: italic;
}

</style>

<style lang="scss">
.drag-ghost {
  background: rgba(0, 0, 0, 0.2);
  .night & {
    background: rgba(255, 255, 255, 0.2);
  }
}
.drag-chosen {
  background: rgba(0, 0, 0, 0.2);
  .night & {
    background: rgba(255, 255, 255, 0.2);
  }
}
</style>
