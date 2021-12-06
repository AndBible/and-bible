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
  <div v-if="!exportMode" class="menu" style="display: flex;">
    <ButtonRow>
      <div class="journal-button" @click="editBookmark">
        <FontAwesomeIcon icon="info-circle"/>
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

<script>
import ButtonRow from "@/components/ButtonRow";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import LabelList from "@/components/LabelList";
import BookmarkText from "@/components/BookmarkText";
import {useCommon} from "@/composables";
import {emit as ebEmit, Events} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import EditableText from "@/components/EditableText";
import AreYouSure from "@/components/modals/AreYouSure";
import {isBottomHalfClicked} from "@/utils";

export default {
  name: "MyNoteRow",
  components: {ButtonRow, FontAwesomeIcon, LabelList, BookmarkText, EditableText, AreYouSure},
  props: {
    bookmark: {type: Object, required: true},
  },
  setup: function (props) {
    const android = inject("android");
    const expanded = ref(false);

    function editBookmark(event) {
      ebEmit(Events.BOOKMARK_CLICKED, props.bookmark.id, {locateTop: isBottomHalfClicked(event)})
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
    const exportMode = inject("exportMode", ref(false));
    const bibleUrl = computed(
      () => {
        const osis = props.bookmark.wholeVerse
          ? props.bookmark.osisRef
          : `${props.bookmark.bookInitials}:${props.bookmark.osisRef}`;
        return `osis://?osis=${osis}&v11n=${props.bookmark.v11n}`;
      }
    );

    return {
      save, bibleUrl, areYouSureDelete, editBookmark, deleteEntry, editor: ref(null), exportMode,
      expanded, ...useCommon()
    }
  },
}
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
