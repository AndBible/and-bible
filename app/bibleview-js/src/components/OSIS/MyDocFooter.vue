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
      <a :href="editLink" class="mydoc-action-link">
        <FontAwesomeIcon :icon="faEdit" class="mydoc-action-icon"/>
        {{ strings.myDocumentEdit }}
      </a>
      <template v-if="isAiPage">
        <span class="mydoc-action-separator">|</span>
        <a :href="regenerateLink" class="mydoc-action-link">
          <FontAwesomeIcon :icon="faSync" class="mydoc-action-icon"/>
          {{ strings.aiDocumentRegenerate }}
        </a>
        <span class="mydoc-action-separator">|</span>
        <a :href="deleteLink" class="mydoc-action-link">
          <FontAwesomeIcon :icon="faTrash" class="mydoc-action-icon"/>
          {{ strings.aiDocumentDelete }}
        </a>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faEdit, faSync, faTrash} from "@fortawesome/free-solid-svg-icons";
import {useCommon} from "@/composables";

const props = defineProps<{
    pageId: string
    isAiPage?: boolean
}>();

const {strings} = useCommon();

const editLink = computed(() => `ab-action://edit?pageId=${encodeURIComponent(props.pageId)}`);
const regenerateLink = computed(() => `ab-action://regenerate?pageId=${encodeURIComponent(props.pageId)}`);
const deleteLink = computed(() => `ab-action://delete?pageId=${encodeURIComponent(props.pageId)}`);
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

.mydoc-action-link {
    color: var(--link-color);
    text-decoration: none;
    padding: 0.25em 0.5em;

    &:hover {
        text-decoration: underline;
    }
}

.mydoc-action-icon {
    margin-right: 0.25em;
}

.mydoc-action-separator {
    margin: 0 0.5em;
    color: #999;
}

.night .mydoc-footer {
    hr {
        border-top-color: #444;
    }

    .mydoc-action-separator {
        color: #666;
    }
}
</style>
