<!--
  - Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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
  <div class="ai-footer">
    <hr />
    <div class="ai-footer-actions">
      <a :href="regenerateLink" class="ai-action-link">
        <span class="ai-action-icon">&#x1F504;</span>
        {{ strings.aiDocumentRegenerate }}
      </a>
      <span class="ai-action-separator">|</span>
      <a :href="deleteLink" class="ai-action-link">
        <span class="ai-action-icon">&#x1F5D1;</span>
        {{ strings.aiDocumentDelete }}
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {useCommon} from "@/composables";

const props = defineProps<{
    pageId: string
}>();

const {strings} = useCommon();

const regenerateLink = computed(() => `ab-action://regenerate?pageId=${encodeURIComponent(props.pageId)}`);
const deleteLink = computed(() => `ab-action://delete?pageId=${encodeURIComponent(props.pageId)}`);
</script>

<style scoped lang="scss">
.ai-footer {
    margin-top: 1em;
    padding-top: 0.5em;

    hr {
        border: none;
        border-top: 1px solid #ccc;
        margin-bottom: 0.5em;
    }
}

.ai-footer-actions {
    text-align: center;
    font-size: 0.9em;
}

.ai-action-link {
    color: var(--link-color);
    text-decoration: none;
    padding: 0.25em 0.5em;

    &:hover {
        text-decoration: underline;
    }
}

.ai-action-icon {
    margin-right: 0.25em;
}

.ai-action-separator {
    margin: 0 0.5em;
    color: #999;
}

// Night mode adjustments
.night .ai-footer {
    hr {
        border-top-color: #444;
    }

    .ai-action-link {
        color: var(--link-color);
    }

    .ai-action-separator {
        color: #666;
    }
}
</style>
