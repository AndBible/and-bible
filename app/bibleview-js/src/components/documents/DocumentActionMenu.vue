<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div class="document-action-menu">
    <ButtonRow>
      <WholePageBookmarks ref="wholePageBookmarks" :book-initials="document.bookInitials" :book-key="document.annotateRef"/>
      <div v-if="document.isMyDocument && document.myDocumentPageId" class="journal-button" @click.stop="emit('start_mydocument_edit', document.myDocumentPageId)">
        <FontAwesomeIcon :icon="faEdit"/>
      </div>
      <template v-if="document.isMyDocument && document.myDocumentPageId">
        <div class="journal-button" @click.stop="android.shareMyDocumentContent(document.bookInitials, document.osisRef)">
          <FontAwesomeIcon :icon="faShareAlt"/>
        </div>
        <div class="journal-button" @click.stop="android.copyMyDocumentContent(document.bookInitials, document.osisRef)">
          <FontAwesomeIcon :icon="faCopy"/>
        </div>
      </template>
      <template v-if="document.isMyDocument && document.sourcePromptId">
        <div class="journal-button" @click.stop="android.regenerateMyDocumentPage(document.myDocumentPageId!)">
          <FontAwesomeIcon :icon="faSync"/>
        </div>
        <div class="journal-button" @click.stop="android.deleteMyDocumentPage(document.myDocumentPageId!)">
          <FontAwesomeIcon :icon="faTrash"/>
        </div>
      </template>

      <template #menubutton>
        <div class="journal-button">
          <FontAwesomeIcon :icon="hasBookmarks ? faBookmark : faEllipsisH"/>
        </div>
      </template>
    </ButtonRow>
  </div>
</template>

<script setup lang="ts">
import {computed, inject, ref} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faBookmark, faCopy, faEdit, faEllipsisH, faShareAlt, faSync, faTrash} from "@fortawesome/free-solid-svg-icons";
import {androidKey} from "@/types/constants";
import {emit} from "@/eventbus";
import ButtonRow from "@/components/ButtonRow.vue";
import WholePageBookmarks from "@/components/WholePageBookmarks.vue";
import type {OsisDocument} from "@/types/documents";

defineProps<{
  document: OsisDocument
}>();

const android = inject(androidKey)!;
const wholePageBookmarks = ref<InstanceType<typeof WholePageBookmarks>>();
const hasBookmarks = computed(() => (wholePageBookmarks.value?.visibleBookmarks.length ?? 0) > 0);
</script>

<style scoped lang="scss">
.document-action-menu {
  float: right;
  position: relative;
  z-index: 1;
}
</style>
