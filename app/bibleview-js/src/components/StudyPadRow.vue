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
  <AreYouSure ref="areYouSureDelete">
    <template #title>
      {{ strings.removeStudyPadConfirmationTitle }}
    </template>
    {{ strings.doYouWantToDeleteEntry }}
  </AreYouSure>
  <div class="entry" :class="{editMode}">
    <div class="menu" :class="{isText: journalEntry.type === 'journal'}">
      <ButtonRow show-drag-handle v-if="!exportMode">
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

        <div v-if="journalEntry.type==='bookmark'" class="journal-button" @click="changeExpanded(!bookmarkEntry.expandContent)">
          <FontAwesomeIcon :icon="bookmarkEntry.expandContent ? 'compress-arrows-alt' : 'expand-arrows-alt'"/>
        </div>

        <div class="journal-button" @click="deleteEntry">
          <FontAwesomeIcon icon="trash"/>
        </div>
        <div v-if="journalEntry.type==='bookmark'" class="journal-button" @click.stop="editBookmark">
          <FontAwesomeIcon icon="info-circle"/>
        </div>
      </ButtonRow>
    </div>
    <template v-if="journalEntry.type==='bookmark'">
      <b><a :href="bibleUrl">{{ bookmarkEntry.bookInitials ? sprintf(strings.multiDocumentLink, bookmarkEntry.verseRangeAbbreviated, bookmarkEntry.bookAbbreviation ) : bookmarkEntry.verseRangeAbbreviated }}</a></b>&nbsp;
      <BookmarkText :expanded="bookmarkEntry.expandContent" :bookmark="bookmarkEntry"/>
      <div v-if="(bookmarkEntry.hasNote || editMode) && bookmarkEntry.expandContent" class="note-separator"/>
    </template>
    <div :class="{'studypad-text-entry': journalEntry.type === 'journal', notes: journalEntry.type === 'bookmark'}">
      <EditableText
        ref="editor"
        :show-placeholder="journalEntry.type === 'journal'"
        :edit-directly="textEntry.new"
        :text="journalText"
        @opened="$emit('edit-opened')"
        @save="journalTextChanged"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import BookmarkText from "@/components/BookmarkText.vue";
import EditableText from "@/components/EditableText.vue";
import ButtonRow from "@/components/ButtonRow.vue";
import {emit as ebEmit, Events} from "@/eventbus";
import {inject, computed, ref} from "vue";
import AreYouSure from "@/components/modals/AreYouSure.vue";
import {androidKey, exportModeKey} from "@/types/constants";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {useCommon} from "@/composables";
import {isBottomHalfClicked} from "@/utils";
import {Label, StudyPadBookmarkItem, StudyPadItem, StudyPadTextItem} from "@/types/client-objects";
import {AreYouSureButton} from "@/types/common";

const emit = defineEmits(['edit-opened', 'add'])
const props = defineProps<{
    journalEntry: StudyPadItem
    label: Label
}>();

const bookmarkEntry = computed(() => props.journalEntry as StudyPadBookmarkItem)
const textEntry = computed(() => props.journalEntry as StudyPadTextItem)

const android = inject(androidKey)!;
const areYouSureDelete = ref<InstanceType<typeof AreYouSure> | null>(null);
const {strings, sprintf} = useCommon();
const editor = ref<InstanceType<typeof EditableText>|null>(null);

const exportMode = inject(exportModeKey, ref(false));

const editMode = computed<boolean>({
  get() {
    return !!editor.value && editor.value.editMode;
  },
  set(value) {
    editor.value!.editMode = value;
  }
});

function journalTextChanged(newText: string) {
  if (props.journalEntry.type === "bookmark") {
    android.saveBookmarkNote(props.journalEntry.id, newText);
  } else if (props.journalEntry.type === "journal") {
    android.updateJournalEntry(props.journalEntry, {text: newText});
  }
}

const journalText = computed(() => {
  if (props.journalEntry.type === "bookmark") return (props.journalEntry as StudyPadBookmarkItem).notes;
  else if (props.journalEntry.type === "journal") return (props.journalEntry as StudyPadTextItem).text;
  return null;
});

function editBookmark(event: MouseEvent) {
  ebEmit(Events.BOOKMARK_CLICKED, props.journalEntry.id, {openInfo: true, locateTop: isBottomHalfClicked(event)})
}

function addNewEntryAfter() {
  emit("add")
  android.createNewJournalEntry(props.label.id, props.journalEntry.type, props.journalEntry.id);
}

async function deleteEntry() {
  if (props.journalEntry.type === "journal") {
    const answer = await areYouSureDelete.value!.areYouSure();
    if (answer) android.deleteJournalEntry((props.journalEntry as StudyPadTextItem).id);
  } else if (props.journalEntry.type === "bookmark") {
    const bookmarkItem = props.journalEntry as StudyPadBookmarkItem
    let answer: "bookmark"|"only_label"|undefined;
    if (bookmarkItem.labels.length > 1) {
      const buttons: AreYouSureButton[] = [{
        title: strings.onlyLabel,
        result: "only_label",
        class: "warning",
      }, {
        title: strings.wholeBookmark,
        result: "bookmark",
        class: "warning",
      }];
      answer = await areYouSureDelete.value!.areYouSure(buttons);
    } else if (await areYouSureDelete.value!.areYouSure()) {
      answer = "bookmark"
    }
    if (answer === "only_label") {
      android.removeBookmarkLabel(props.journalEntry.id, props.label.id);
    } else if (answer === "bookmark") {
      android.removeBookmark(props.journalEntry.id);
    }
  }
}

function indent(change: number) {
  android.updateJournalEntry(props.journalEntry, {indentLevel: props.journalEntry.indentLevel + change})
}

function changeExpanded(newValue: boolean) {
  android.updateJournalEntry(props.journalEntry, {expandContent: newValue})
}

const bibleUrl = computed(
  () => {
    const bookmarkItem = props.journalEntry as StudyPadBookmarkItem
    const osis = bookmarkItem.osisRef;
    return `osis://?osis=${osis}&v11n=${bookmarkItem.v11n}`;
  }
);
defineExpose({editor});
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
