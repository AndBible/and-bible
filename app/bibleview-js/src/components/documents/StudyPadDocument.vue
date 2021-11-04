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
  <div :class="{exportMode}">
    <div class="journal-name" :style="labelNameStyle">
      {{ document.label.name }}
    </div>
    <div v-if="journalEntries.length === 0 && !exportMode">
      {{strings.emptyStudyPad}}
      <span class="journal-button" v-if="!exportMode" @click="addNewEntry">
        <FontAwesomeIcon icon="plus-circle"/>
      </span>
    </div>
    <draggable
      v-model="journalEntries"
      handle=".drag-handle"
      group="journal-entries"
      ghost-class="drag-ghost"
      chosen-class="drag-chosen"
      :item-key="(e) => `studypad-${e.type}-${e.id}`"
    >
      <template #item="{element: j}">
        <div
          :id="`o-${studyPadOrdinal(j)}`"
          :data-ordinal="studyPadOrdinal(j)"
          class="ordinal"
        >
          <div class="studypad-container" :style="indentStyle(j)" :id="`studypad-${j.type}-${j.id}`">
            <StudyPadRow
              :ref="setStudyPadRowRef"
              :journal-entry="j"
              :label="document.label"
              @add="adding=true"
            />
          </div>
        </div>
      </template>
    </draggable>
    <div v-if="journalEntries.length > 0 && !exportMode">
      <span v-if="lastEntry.type === StudyPadEntryTypes.BOOKMARK && !lastEntry.hasNote" class="journal-button" @click="editLastNote">
        <FontAwesomeIcon icon="edit"/>
      </span>
      <span class="journal-button" @click="appendNewEntry">
        <FontAwesomeIcon icon="plus-circle"/>
      </span>
    </div>
  </div>
</template>

<script>
import {computed, ref} from "@vue/reactivity";
import {inject, provide, nextTick, onBeforeUpdate} from "@vue/runtime-core";
import {useCommon, useJournal} from "@/composables";
import {Events, setupEventBusListener} from "@/eventbus";
import {groupBy, sortBy} from "lodash";
import StudyPadRow from "@/components/StudyPadRow";
import draggable from "vuedraggable";
import {StudyPadEntryTypes} from "@/constants";
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
    const studyPadRowRefs = [];
    const exportMode = inject("exportMode", ref(false));

    function setStudyPadRowRef(el) {
      if(el) {
        studyPadRowRefs.push(el);
      }
    }

    onBeforeUpdate(() => {
      studyPadRowRefs.splice(0);
    });

    function editLastNote() {
      const lastRow = studyPadRowRefs[studyPadRowRefs.length - 1];
      lastRow.editor.editMode = true;
    }

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
          if(v.type === StudyPadEntryTypes.BOOKMARK) {
            v.bookmarkToLabel.orderNumber = newOrder;
          }
          v.orderNumber = newOrder;

        }
        const grouped = groupBy(changed, "type");
        const bookmarks = grouped[StudyPadEntryTypes.BOOKMARK] || [];
        const journals = grouped[StudyPadEntryTypes.JOURNAL_TEXT] || [];
        android.updateOrderNumber(label.id, bookmarks, journals);
      }
    });
    const adding = ref(false);

    const lastEntry = computed(() => journalEntries.value[journalEntries.value.length - 1]);

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
        scrollToId(`studypad-${journal.type}-${journal.id}`, {duration: 300, onlyIfInvisible: true})
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
      adding.value = true;
      android.createNewJournalEntry(label.id);
    }

    function appendNewEntry() {
      adding.value = true;
      android.createNewJournalEntry(label.id, lastEntry.value.type, lastEntry.value.id);
    }

    const labelNameStyle = computed(() => {
      if(exportMode.value) return null;
      const color = adjustedColorOrig(label.style.color);
      const textColor = color.isLight() ? "var(--label-text-black)": "var(--label-text-white)";
      return `background-color: ${color.string()}; color: ${textColor};`;
    });

    function studyPadOrdinal(journalEntry) {
      if(journalEntry.type === StudyPadEntryTypes.BOOKMARK) {
        return journalEntry.id;
      } else {
        return journalEntry.id + 10000;
      }
    }

    return {
      lastEntry, journalEntries, editNotes, adding, indentStyle, editableJournalEntry,  addNewEntry, appendNewEntry,
      labelNameStyle, studyPadOrdinal, StudyPadEntryTypes, setStudyPadRowRef, editLastNote, exportMode, ...useCommon()
    }
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

div.journal-name {
  padding-top: 10px;
  padding-bottom: 10px;
  padding-left: 5px;
  margin-bottom: 10px;
  line-height: normal;
  font-size: 180%;
  border-radius: 10px;
  margin-inline-start: 0px;
  font-weight: bold;
  position: relative;
}

.share-button {
  position:absolute;
  padding: 10px;
  [dir=ltr] & {
    right: 0;
  }
  [dir=rtl] & {
    left: 0;
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
