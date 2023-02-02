<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div :class="{exportMode}">
    <div class="journal-name" :style="labelNameStyle">
      {{ document.label.name }}
    </div>
    <div v-if="journalEntries.length === 0 && !exportMode">
      {{ strings.emptyStudyPad }}
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
        :item-key="journalItemKey"
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
      <span v-if="lastEntry.type === 'bookmark' && !asBookmarkItem(lastEntry).hasNote" class="journal-button"
            @click="editLastNote">
        <FontAwesomeIcon icon="edit"/>
      </span>
      <span class="journal-button" @click="appendNewEntry">
        <FontAwesomeIcon icon="plus-circle"/>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, inject, nextTick, onBeforeUpdate, provide, ref, Ref} from "vue";
import {useCommon} from "@/composables";
import {setupEventBusListener} from "@/eventbus";
import {groupBy, sortBy} from "lodash";
import StudyPadRow from "@/components/StudyPadRow.vue";
import {androidKey, exportModeKey, globalBookmarksKey, scrollKey} from "@/types/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {adjustedColorOrig} from "@/utils";
import {useJournal} from "@/composables/journal";
import {StudyPadDocument} from "@/types/documents";
import {BookmarkToLabel, StudyPadBookmarkItem, StudyPadItem, StudyPadTextItem} from "@/types/client-objects";
import Color from "color";
import draggable from "vuedraggable";

const props = defineProps<{ document: StudyPadDocument }>();

// eslint-disable-next-line vue/no-setup-props-destructure
const {bookmarks, label, journalTextEntries: journalTextEntries_, bookmarkToLabels: bookmarkToLabels_} = props.document;
const journal = useJournal(label);
provide("journal", journal);
const {scrollToId} = inject(scrollKey)!;
const android = inject(androidKey)!;

type StudyPadRowType = InstanceType<typeof StudyPadRow>

function asBookmarkItem(item: StudyPadItem) {
    return item as StudyPadBookmarkItem
}

const studyPadRowRefs: StudyPadRowType[] = [];
const exportMode = inject(exportModeKey, ref(false));


function setStudyPadRowRef(el: any) { // They are of StudyPadRowType but vue types for :ref are broken
    if (el) {
        studyPadRowRefs.push(el);
    }
}

onBeforeUpdate(() => {
    studyPadRowRefs.splice(0);
});

function editLastNote() {
    const lastRow = studyPadRowRefs[studyPadRowRefs.length - 1];
    lastRow.editor!.editMode = true;
}

const {
    journalTextEntries, updateBookmarkToLabels, updateJournalTextEntries,
    updateJournalOrdering, deleteJournal, bookmarkToLabels
} = journal;

updateJournalTextEntries(...journalTextEntries_);
updateBookmarkToLabels(...bookmarkToLabels_)

const globalBookmarks = inject(globalBookmarksKey)!;

globalBookmarks.updateBookmarks(bookmarks);

const journalEntries: Ref<StudyPadItem[]> = computed({
    get:
        () => {
            let entries = [];
            entries.push(...globalBookmarks.bookmarks.value.filter(b => b.labels.includes(label.id)).map(b => {
                const bookmarkToLabel = bookmarkToLabels.get(b.id);
                return {
                    ...b,
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
        const changed: StudyPadItem[] = [];
        for (const v of values) {
            const newOrder = count++
            if (v.orderNumber !== newOrder) {
                changed.push(v);
            }
            if (v.type === "bookmark") {
                v.bookmarkToLabel.orderNumber = newOrder;
            }
            v.orderNumber = newOrder;

        }
        const grouped = groupBy(changed, "type");
        const bookmarks: StudyPadBookmarkItem[] = (grouped["bookmark"] || []) as StudyPadBookmarkItem[];
        const journals: StudyPadTextItem[] = (grouped["journal"] || []) as StudyPadTextItem[];
        android.updateOrderNumber(label.id, bookmarks, journals);
    }
});
const adding = ref(false);

const lastEntry = computed(() => journalEntries.value[journalEntries.value.length - 1]);

setupEventBusListener("add_or_update_journal", async (
    {
        journal,
        bookmarkToLabelsOrdered,
        journalsOrdered
    }: {
        journal: StudyPadTextItem,
        bookmarkToLabelsOrdered: BookmarkToLabel[],
        journalsOrdered: StudyPadTextItem[]
    }) =>
{
    if (journal && adding.value) {
        journal.new = true
        adding.value = false;
    }
    updateBookmarkToLabels(...bookmarkToLabelsOrdered);
    updateJournalOrdering(...journalsOrdered);
    if (journal) {
        updateJournalTextEntries(journal);
    }
    await nextTick();
    if (journal && journal.new) {
        scrollToId(`studypad-${journal.type}-${journal.id}`, {duration: 300, onlyIfInvisible: true})
    }
})

setupEventBusListener("add_or_update_bookmark_to_label", (bookmarkToLabel: BookmarkToLabel) => {
    updateBookmarkToLabels(bookmarkToLabel);
})

setupEventBusListener("delete_journal", (journalId: number) => {
    deleteJournal(journalId)
})

function indentStyle(entry: StudyPadItem) {
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
    if (exportMode.value) return;
    const color: Color = adjustedColorOrig(label.style.color)!;
    const textColor = color.isLight() ? "var(--label-text-black)" : "var(--label-text-white)";
    return `background-color: ${color.string()}; color: ${textColor};`;
});

function studyPadOrdinal(journalEntry: StudyPadItem) {
    if (journalEntry.type === "bookmark") {
        return journalEntry.id;
    } else {
        return journalEntry.id + 10000;
    }
}

const journalItemKey = (e: StudyPadItem) => `studypad-${e.type}-${e.id}`

const {strings} = useCommon()
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
  margin-inline-start: 0;
  font-weight: bold;
  position: relative;
}

.share-button {
  position: absolute;
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
