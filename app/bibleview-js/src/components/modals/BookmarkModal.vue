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
  <Modal v-if="showBookmark" @close="closeBookmark">
    <EditableText
        v-if="!infoShown"
        constraint-height
        :edit-directly="editDirectly" :text="bookmark.notes || ''"
        @changed="changeNote"
        max-height="inherit"
    />
    <div v-show="infoShown" class="info">
      <div v-if="bookmark.bookName">
        {{ sprintf(strings.bookmarkAccurate, bookmark.bookName) }}
      </div>
      {{ sprintf(strings.createdAt, formatTimestamp(bookmark.createdAt)) }}<br/>
      {{ sprintf(strings.lastUpdatedOn, formatTimestamp(bookmark.lastUpdatedOn)) }}<br/>
      <div v-if="bookmark.notes" class="my-notes-link">
        <a :href="`my-notes://?id=${bookmark.id}`">{{ strings.openMyNotes }}</a>
      </div>
    </div>
    <template #title>
      <span :style="`color:${labelColor}`">
        <FontAwesomeIcon v-if="bookmark.notes" icon="edit"/>
        <FontAwesomeIcon v-else icon="bookmark"/>
      </span>
      {{ bookmark.verseRangeAbbreviated }} <LabelList :labels="labels"/>
    </template>
    <template #footer>
      <button class="button" @click="removeBookmark">{{strings.removeBookmark}}</button>
      <button class="button" @click="assignLabels">{{strings.assignLabels}}</button>
      <button :class="{'button': true, toggled: infoShown}" @click="infoShown = !infoShown">{{strings.bookmarkInfo}}</button>
      <button class="button right" @click="closeBookmark">{{strings.closeModal}}</button>
    </template>
  </Modal>
  <AreYouSure ref="areYouSure">
    <template #title>
      {{ strings.removeBookmarkConfirmationTitle }}
    </template>
    {{ strings.removeBookmarkConfirmation }}
  </AreYouSure>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {Events, setupEventBusListener} from "@/eventbus";
import {computed, ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import AreYouSure from "@/components/modals/AreYouSure";
import Color from "color";
import EditableText from "@/components/EditableText";
import {debounce} from "lodash";
import LabelList from "@/components/LabelList";

export default {
  name: "BookmarkModal",
  components: {LabelList, EditableText, Modal, FontAwesomeIcon, AreYouSure},
  setup() {
    const showBookmark = ref(false);
    const android = inject("android");
    const bookmark = ref(null);
    const areYouSure = ref(null);
    const infoShown = ref(false);
    const label = ref({});
    const labels = ref([]);
    const editDirectly = ref(false);
    setupEventBusListener(Events.BOOKMARK_FLAG_CLICKED, (b, labels_, {open = false} = {}) => {
      editDirectly.value = open || !b.notes;
      if(!showBookmark.value) infoShown.value = false;
      showBookmark.value = true;
      bookmark.value = b;
      label.value = labels_[0];
      labels.value = labels_;
    })

    function closeBookmark() {
      showBookmark.value = false;
      android.saveBookmarkNote(bookmark.value.id, bookmark.value.notes);
    }

    function assignLabels() {
      android.assignLabels(bookmark.value.id);
    }

    async function removeBookmark() {
      if(await areYouSure.value.areYouSure()) {
        showBookmark.value = false;
        android.removeBookmark(bookmark.value.id);
      }
    }

    const bookmarkComputed = computed(() => {
      if(bookmark.value) return bookmark.value;
      return {
        id: null,
        notes: null,
      }
    });

    const {adjustedColor, ...common} = useCommon();

    const labelColor = computed(() => {
        return adjustedColor(label.value.color).string();
    });

    const changeNote = debounce((text) => {
      bookmark.value.notes = text;
    }, 500)

    return {
      showBookmark, closeBookmark, areYouSure, infoShown, editDirectly,
      removeBookmark,  assignLabels,  bookmark: bookmarkComputed, labelColor, changeNote, labels,
      ...common
    };
  },
}
</script>

<style scoped lang="scss">
.info {
  margin-top: 10pt;
  font-size: 80%;
}
.my-notes-link {
  padding-top: 10pt;
  padding-bottom: 5pt;

  font-size: smaller;
}


</style>
