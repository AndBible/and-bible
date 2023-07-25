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
  <ModalDialog v-if="showBookmark && bookmark" @close="closeBookmark" wide :locate-top="locateTop" :edit="!infoShown">
    <template #title-div>
      <div class="bookmark-title" style="width: calc(100% - 80px);">
        <div class="overlay"/>
        <div style="overflow-x: auto">
          <LabelList single-line handle-touch in-bookmark :bookmark-id="bookmark.id" ref="labelList"/>
        </div>
        <div class="title-text one-liner">
          <template v-if="isBibleBookmark(bookmark)">{{ bookmark.verseRangeAbbreviated }}</template>
          <q v-if="bookmark.text"><i>{{ bookmark.text }}</i></q>
        </div>
      </div>
    </template>

    <template #extra-buttons>
      <div class="modal-action-button" @click="toggleInfo" @mousedown="toggleInfo" @touchstart="toggleInfo">
        <template v-if="infoShown">
          <FontAwesomeIcon icon="edit"/>
        </template>
        <template v-else>
          <FontAwesomeIcon icon="info-circle"/>
        </template>
      </div>
    </template>

    <EditableText
        v-if="!infoShown"
        constraint-display-height
        :text="bookmarkNotes || ''"
        @save="changeNote"
        show-placeholder
        :edit-directly="editDirectly"
    >
      {{ strings.editBookmarkPlaceholder }}
    </EditableText>
    <div v-if="infoShown" class="info">
      <BookmarkButtons
          :bookmark="bookmark"
          in-bookmark-modal
          @close-bookmark="showBookmark = false"
      />
      <div class="bible-text">
        <BookmarkText expanded :bookmark="bookmark"/>
      </div>
      <div class="links">
        <div
            v-if="isBibleBookmark(bookmark)"
            class="link-line"
        >
          <span class="link-icon"><FontAwesomeIcon icon="file-alt"/></span>
          <a :href="`my-notes://?ordinal=${bookmark.originalOrdinalRange[0]}&v11n=${bookmark.v11n}`">{{
              strings.openMyNotes
            }}</a>
        </div>
        <div v-for="label in labels.filter(l => l.isRealLabel)" :key="`label-${bookmark.id}-${label.id}`"
             class="link-line">
          <span class="link-icon" :style="`color: ${adjustedColor(label.color).string()};`"><FontAwesomeIcon
              icon="file-alt"/></span>
          <a :href="`journal://?id=${label.id}&bookmarkId=${bookmark.id}`">{{
              sprintf(strings.openStudyPad, label.name)
            }}</a>
        </div>
      </div>
      <div class="info-text">
        <div class="separator"/>
        <div v-if="bookmark.bookName">
          <span v-html="sprintf(strings.bookmarkAccurate, originalBookLink)"/>
        </div>
        <div v-else>
          <span v-html="sprintf(strings.bookmarkInaccurate, originalBookLink)"/>
        </div>
        <div v-if="bookmark.createdAt !== bookmark.lastUpdatedOn">
          {{ sprintf(strings.lastUpdatedOn, formatTimestamp(bookmark.lastUpdatedOn)) }}<br/>
        </div>
        {{ sprintf(strings.createdAt, formatTimestamp(bookmark.createdAt)) }}<br/>
      </div>
    </div>
  </ModalDialog>
</template>

<script lang="ts" setup>
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {setupEventBusListener} from "@/eventbus";
import {computed, inject, nextTick, provide, ref} from "vue";
import {useCommon} from "@/composables";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import EditableText from "@/components/EditableText.vue";
import LabelList from "@/components/LabelList.vue";
import BookmarkText from "@/components/BookmarkText.vue";
import BookmarkButtons from "@/components/BookmarkButtons.vue";
import {clickWaiter} from "@/utils";
import {sortBy} from "lodash";
import {androidKey, globalBookmarksKey, locateTopKey} from "@/types/constants";
import {BaseBookmark} from "@/types/client-objects";
import {isBibleBookmark, isGenericBookmark} from "@/composables/bookmarks";

const showBookmark = ref(false);
const android = inject(androidKey)!;
const infoShown = ref(false);
const bookmarkId = ref<IdType | null>(null);
const labelList = ref<InstanceType<typeof LabelList> | null>(null);
const locateTop = ref(false);
provide(locateTopKey, locateTop);

const {bookmarkMap, bookmarkLabels} = inject(globalBookmarksKey)!;

const bookmark = computed<BaseBookmark | null>(() => {
    if (!bookmarkId.value) return null;
    return bookmarkMap.get(bookmarkId.value!)!;
});

const labels = computed(() => {
    if (!bookmark.value) return [];
    return sortBy(bookmark.value!.labels.map(l => bookmarkLabels.get(l)!), ["name"])
});

const bookmarkNotes = computed(() => bookmark.value!.notes);
let originalNotes: string | null = null;

setupEventBusListener("bookmark_clicked",
    async (
        bookmarkId_: IdType,
        {
            locateTop: _locateTop = false,
            openLabels = false,
            openInfo = false,
            openNotes = false
        } = {}) => {
        bookmarkId.value = bookmarkId_;
        originalNotes = bookmarkNotes.value;
        infoShown.value = !openNotes && (openInfo || !bookmarkNotes.value);
        editDirectly.value = !infoShown.value && !bookmarkNotes.value;
        locateTop.value = _locateTop;
        showBookmark.value = true;
        if (openLabels && !openNotes) {
            await nextTick();
            labelList.value!.openActions();
        }
    }
);

function closeBookmark() {
    showBookmark.value = false;
    if (originalNotes !== bookmarkNotes.value) {
        android.saveBookmarkNote(bookmark.value!, bookmarkNotes.value);
    }

    originalNotes = null;
}

const {adjustedColor, strings, sprintf, formatTimestamp} = useCommon();

const changeNote = (text: string) => {
    if (bookmark.value) {
        android.saveBookmarkNote(bookmark.value, text);
    }
}

const originalBookLink = computed<string>(() => {
    if (!bookmark.value) return ""
    const doc = bookmark.value!.bookInitials ? bookmark.value!.bookInitials : "";
    if(isBibleBookmark(bookmark.value)) {
        const bibleUrl = encodeURI(`osis://?osis=${doc}:${bookmark.value!.osisRef}&v11n=${bookmark.value!.v11n}`)
        return `<a href="${bibleUrl}">${bookmark.value!.bookName || strings.defaultBook}</a>`;
    } else if(isGenericBookmark(bookmark.value)) {
        const docUrl = encodeURI(`osis://?osis=${bookmark.value!.key}&doc=${doc}&ordinal=${bookmark.value.ordinalRange[0]}`)
        return `<a href="${docUrl}">${bookmark.value!.bookName || strings.defaultBook}</a>`;
    } else {
        throw new Error("Illegal type")
    }
})

const editDirectly = ref(false);

const {waitForClick} = clickWaiter();

async function toggleInfo(event: MouseEvent | TouchEvent) {
    if (!await waitForClick(event)) return;
    infoShown.value = !infoShown.value
    editDirectly.value = !infoShown.value && !bookmarkNotes.value;
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.bookmark-title {
  padding-top: 2px;
}

.title-text {
  cursor: default;
  font-weight: normal;
  padding-top: 2px;
}


.modal-toolbar {
  align-self: flex-end;
  display: flex;
}

.link-icon {
  padding: 2px;
  padding-inline-end: 4px;
  color: $button-grey;
}

.info-text {
  font-size: 85%;
}

.info {
  @extend .visible-scrollbar;
  font-size: 90%;
  overflow-y: auto;

  max-height: calc(var(--max-height) - 25pt);
}

.links {
  padding-top: 10pt;
  padding-bottom: 5pt;

  .link-line {
    padding: 4px;
  }
}

.bible-text {
  text-indent: 5pt;
  font-style: italic;
}

.overlay {
  position: absolute;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0), $modal-header-background-color 75%, $modal-header-background-color 100%);

  .night & {
    background: linear-gradient(90deg, rgba(0, 0, 0, 0), $night-modal-header-background-color 75%, $night-modal-header-background-color 100%);
  }

  [dir=ltr] & {
    right: 80px;
  }

  [dir=rtl] & {
    left: 80px;
  }

  top: 0;
  width: 20px;
  height: 2em;
}
</style>
