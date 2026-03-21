<!--
  - Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div
      :id="`doc-${document.id}`"
      class="document"
      :data-book-initials="bookInitials"
      :data-osis-ref="osisRef"
  >
    <div v-if="editMode" class="mydoc-edit-container">
      <EditableText
        :text="editContent"
        :content-type="editContentType"
        :note-editor-context="editPageId ? { entityType: 'MY_DOCUMENT_PAGE', entityId: editPageId } : null"
        :edit-directly="true"
        @save="handleSave"
        @closed="handleEditClosed"
      />
    </div>

    <template v-else>
      <DocumentActionMenu :document="document"/>
      <OsisFragment :is-native-html="document.isNativeHtml" :fragment="osisFragment"/>
      <div v-if="document.isAiDocument && document.sourcePromptName" class="ai-footer">
        <a v-if="document.sourcePromptId" class="prompt-link" @click.prevent="android.openPromptEditor(document.sourcePromptId!)">{{ document.sourcePromptName }}</a>
        <span v-else>{{ document.sourcePromptName }}</span>
        <span v-if="document.sourceModelName" class="model-name"> ({{ document.sourceModelName }})</span>
      </div>
      <OpenAllLink v-if="document.bookCategory != 'GENERAL_BOOK'" :v11n="document.v11n"/>
      <FeaturesLink :fragment="osisFragment"/>
    </template>
  </div>
</template>

<script setup lang="ts">
import OsisFragment from "@/components/documents/OsisFragment.vue";
import DocumentActionMenu from "@/components/documents/DocumentActionMenu.vue";
import EditableText from "@/components/EditableText.vue";
import FeaturesLink from "@/components/FeaturesLink.vue";
import OpenAllLink from "@/components/OpenAllLink.vue";
import {useCommon, useReferenceCollector} from "@/composables";
import {androidKey, customCssKey, globalBookmarksKey, osisDocumentInfoKey, referenceCollectorKey} from "@/types/constants";
import {inject, provide, ref} from "vue";
import {OsisDocument} from "@/types/documents";
import {useBookmarks} from "@/composables/bookmarks";
import {setupEventBusListener} from "@/eventbus";
import {TextContentType} from "@/types/client-objects";

const props = defineProps<{ document: OsisDocument }>();

// eslint-disable-next-line vue/no-setup-props-destructure,no-unused-vars
const {
    id,
    ordinalRange,
    osisFragment,
    bookCategory,
    bookInitials,
    annotateRef,
    osisRef,
    genericBookmarks,
    highlightedOrdinalRange,
    isMyDocument,
} = props.document;
const referenceCollector = useReferenceCollector();

const globalBookmarks = inject(globalBookmarksKey)!;
const {registerBook} = inject(customCssKey)!;
const android = inject(androidKey)!;
globalBookmarks.updateBookmarks(genericBookmarks);

const {config, appSettings, ...common} = useCommon();

useBookmarks(id, ordinalRange, globalBookmarks, bookInitials, annotateRef, false, ref(true), common, config, appSettings);
provide(osisDocumentInfoKey, {bookInitials, highlightedOrdinalRange, osisRef: annotateRef})

registerBook(`epub/${bookInitials}/${osisRef}`)

if (bookCategory === "COMMENTARY" || bookCategory === "GENERAL_BOOK") {
    provide(referenceCollectorKey, referenceCollector);
}

// MyDocument editing state
const editMode = ref(false);
const editContent = ref<string>("");
const editContentType = ref<TextContentType>("MARKDOWN");
const editPageId = ref<string>("");

async function startEditing() {
    const info = await android.getMyDocumentPageRawContent(bookInitials, osisRef);
    if (!info) return;
    editPageId.value = info.pageId;
    editContent.value = info.content;
    editContentType.value = info.contentType as TextContentType;
    editMode.value = true;
}

function handleSave(newText: string) {
    editContent.value = newText;
    android.saveMyDocumentPageContent(bookInitials, editPageId.value, newText, null);
}

function handleEditClosed() {
    editMode.value = false;
    android.reloadMyDocumentPage(bookInitials);
}

if (isMyDocument) {
    setupEventBusListener("start_mydocument_edit", (targetPageId: string) => {
        if (targetPageId === props.document.myDocumentPageId) {
            startEditing();
        }
    });
}
</script>

<style lang="scss" scoped>
.document {
  overflow: hidden; // Creates BFC so floated DocumentActionMenu stays within this document
}

.mydoc-edit-container {
  border: 2px solid rgba(0, 0, 255, 0.5);
  border-radius: 5px;
  margin: 4px;

  .monochrome & {
    border-color: black;
  }
  .monochrome.night & {
    border-color: white;
  }
}

.ai-footer {
  margin-top: 1em;
  padding-top: 0.5em;
  font-size: 0.8em;
  opacity: 0.6;
  text-align: right;

  .prompt-link {
    cursor: pointer;
    text-decoration: underline;
  }
}
</style>
