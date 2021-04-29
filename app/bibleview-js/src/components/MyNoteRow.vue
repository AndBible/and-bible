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
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
  <div class="menu" style="display: flex;">
    <ButtonRow>
      <div class="journal-button" @click="editBookmark">
        <FontAwesomeIcon icon="bookmark"/>
      </div>
      <div v-if="!bookmark.notes" class="journal-button" @click="editor.editMode = true">
        <FontAwesomeIcon icon="edit"/>
      </div>
      <div class="journal-button" @click="deleteEntry">
        <FontAwesomeIcon icon="trash"/>
      </div>
    </ButtonRow>
  </div>
  <div>
    <b><a :href="bookmark.bibleUrl">{{ bookmark.verseRangeOnlyNumber }}</a></b>&nbsp;
    <BookmarkText :bookmark="bookmark" :expanded="expanded" @change-expanded="expanded = $event"/> <LabelList :bookmark-id="bookmark.id"/>
    <div class="notes">
      <EditableText
          ref="editor"
          :text="bookmark.notes"
          @save="save"
      />
    </div>
  </div>
</template>

<script>
import ButtonRow from "@/components/ButtonRow";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import LabelList from "@/components/LabelList";
import BookmarkText from "@/components/BookmarkText";
import {useCommon} from "@/composables";
import {emit as ebEmit, Events} from "@/eventbus";
import {ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import EditableText from "@/components/EditableText";
import AreYouSure from "@/components/modals/AreYouSure";

export default {
  name: "MyNoteRow",
  components: {ButtonRow, FontAwesomeIcon, LabelList, BookmarkText, EditableText, AreYouSure},
  props: {
    bookmark: {type: Object, required: true},
  },
  setup(props) {
    const android = inject("android");
    const expanded = ref(false);

    function editBookmark() {
      ebEmit(Events.BOOKMARK_FLAG_CLICKED, props.bookmark.id)
    }

    function save(newText) {
      android.saveBookmarkNote(props.bookmark.id, newText);
    }

    const areYouSureDelete = ref(null);

    async function deleteEntry() {
      const answer = await areYouSureDelete.value.areYouSure();
      if (answer) {
        android.removeBookmark(props.bookmark.id);
      }
    }

    return {save, areYouSureDelete, editBookmark, deleteEntry, editor: ref(null), expanded, ...useCommon()}
  },
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

</style>
