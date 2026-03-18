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
  <div class="mydoc-footer">
    <hr />
    <div class="mydoc-footer-actions">
      <button class="mydoc-action-button" @click.stop="emit('start_mydocument_edit')">
        <FontAwesomeIcon :icon="faEdit" class="mydoc-action-icon"/>
        {{ strings.myDocumentEdit }}
      </button>
      <template v-if="isAiPage">
        <button class="mydoc-action-button" @click.stop="android.regenerateMyDocumentPage(pageId)">
          <FontAwesomeIcon :icon="faSync" class="mydoc-action-icon"/>
          {{ strings.aiDocumentRegenerate }}
        </button>
        <button class="mydoc-action-button" @click.stop="android.deleteMyDocumentPage(pageId)">
          <FontAwesomeIcon :icon="faTrash" class="mydoc-action-icon"/>
          {{ strings.aiDocumentDelete }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {inject} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faEdit, faSync, faTrash} from "@fortawesome/free-solid-svg-icons";
import {useCommon} from "@/composables";
import {androidKey} from "@/types/constants";
import {emit} from "@/eventbus";

defineProps<{
    pageId: string
    isAiPage?: boolean
}>();

const {strings} = useCommon();
const android = inject(androidKey)!;
</script>

<style scoped lang="scss">
.mydoc-footer {
    margin-top: 1em;
    padding-top: 0.5em;

    hr {
        border: none;
        border-top: 1px solid #ccc;
        margin-bottom: 0.5em;
    }
}

.mydoc-footer-actions {
    text-align: center;
    font-size: 0.9em;
}

.mydoc-action-button {
    background: none;
    border: 1px solid #ccc;
    border-radius: 4px;
    color: var(--text-color);
    padding: 0.3em 0.7em;
    margin: 0 0.2em;
    cursor: pointer;
    font-size: 0.85em;

    &:active {
        background: rgba(0, 0, 0, 0.08);
    }
}

.mydoc-action-icon {
    margin-right: 0.3em;
}

.night .mydoc-footer {
    hr {
        border-top-color: #444;
    }

    .mydoc-action-button {
        border-color: #555;
    }
}
</style>
