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
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
  <div v-if="!exportMode" class="menu" style="display: flex;">
    <ButtonRow>
      <div class="journal-button" @click="editBookmark">
        <FontAwesomeIcon icon="info-circle"/>
      </div>
      <div v-if="!bookmark.notes" class="journal-button" @click="setEditMode(true)">
        <FontAwesomeIcon icon="edit"/>
      </div>
      <div class="journal-button" @click="deleteEntry">
        <FontAwesomeIcon icon="trash"/>
      </div>
    </ButtonRow>
  </div>
  <div>
    <div class="overlay"/>
    <div style="white-space: nowrap; overflow-x: auto">
      <b><a :href="bibleUrl">{{ bookmark.verseRangeOnlyNumber }}</a></b>&nbsp;
      <LabelList v-if="!exportMode" in-bookmark single-line :bookmark-id="bookmark.id"/>
    </div>
    <BookmarkText :bookmark="bookmark" :expanded="expanded || exportMode" @change-expanded="expanded = $event"/>
    <div v-if="bookmark.hasNote && (expanded || exportMode)" class="note-separator"/>
    <div class="notes">
      <EditableText
        ref="editor"
        :text="bookmark.notes"
        @save="save"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import ButtonRow from "@/components/ButtonRow.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import LabelList from "@/components/LabelList.vue";
import BookmarkText from "@/components/BookmarkText.vue";
import {useCommon} from "@/composables";
import {emit as ebEmit, Events} from "@/eventbus";
import {computed, ref, inject} from "vue";
import EditableText from "@/components/EditableText.vue";
import AreYouSure from "@/components/modals/AreYouSure.vue";
import {isBottomHalfClicked} from "@/utils";
import {androidKey, exportModeKey} from "@/types/constants";
import {Bookmark} from "@/types/client-objects";

const props =  defineProps<{bookmark: Bookmark}>()

const android = inject(androidKey)!;
const expanded = ref(false);

function editBookmark(event: MouseEvent) {
  ebEmit(Events.BOOKMARK_CLICKED, props.bookmark.id, {locateTop: isBottomHalfClicked(event)})
}

function save(newText: string) {
  android.saveBookmarkNote(props.bookmark.id, newText);
}

const editor = ref<InstanceType<typeof EditableText>|null>(null);

function setEditMode(value: boolean){
    editor.value!.editMode = value;
}

const areYouSureDelete = ref<InstanceType<typeof AreYouSure>|null>(null);

async function deleteEntry() {
  const answer = await areYouSureDelete.value!.areYouSure();
  if (answer) {
    android.removeBookmark(props.bookmark.id);
  }
}
const exportMode = inject(exportModeKey, ref(false));
const bibleUrl = computed(
  () => {
    const osis = props.bookmark.wholeVerse
      ? props.bookmark.osisRef
      : `${props.bookmark.bookInitials}:${props.bookmark.osisRef}`;
    return `osis://?osis=${osis}&v11n=${props.bookmark.v11n}`;
  }
);
    
const {strings} = useCommon();
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.notes {
  text-indent: 2pt;
  margin-top: 4pt;
}

.overlay {
  position: absolute;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0), var(--background-color) 50%, var(--background-color) 100%);
  .night & {
    background: linear-gradient(90deg, rgba(0, 0, 0, 0), var(--background-color) 75%, var(--background-color) 100%);
  }
  right: 0;
  top: 0;
  width: 50px;
  height: 2em;
}
</style>
