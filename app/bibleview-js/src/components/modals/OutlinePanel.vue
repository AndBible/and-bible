<!--
  - Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <ModalDialog v-if="showModal" @close="close" blocking wide :locate-top="true">
    <template #title>{{ strings.outline }}</template>
    <div v-if="entries.length > 0" class="outline-list">
      <div
          v-for="entry in entries"
          :key="`${entry.isCustom ? 'c' : 'm'}-${entry.ordinal}-${entry.titleIndex ?? ''}-${entry.headingId ?? ''}`"
          class="outline-item"
          :class="`level-${Math.min(entry.level, 6)}`"
          :style="{ paddingInlineStart: `${(entry.level - 1) * 16}px` }"
          @click="navigateTo(entry)"
          @keydown.enter="navigateTo(entry)"
          @keydown.space.prevent="navigateTo(entry)"
          tabindex="0"
          role="button"
      >
        <span class="entry-text">{{ entry.text }}</span>
      </div>
    </div>
    <div v-else class="empty">{{ strings.outlineNoHeadings }}</div>
  </ModalDialog>
</template>

<script lang="ts" setup>
import {ref} from "vue";
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {useCommon} from "@/composables";
import {setupEventBusListener} from "@/eventbus";
import {OutlineEntry} from "@/composables/outline";

const {strings, appSettings} = useCommon();

const showModal = ref(false);
const entries = ref<OutlineEntry[]>([]);
const docId = ref("");

setupEventBusListener("open_outline", (s: { entries: OutlineEntry[], bookInitials: string, documentId: string }) => {
    entries.value = s.entries;
    docId.value = s.documentId;
    showModal.value = true;
});

function navigateTo(entry: OutlineEntry) {
    const el = docId.value
        ? document.querySelector(`#doc-${docId.value} #v-${entry.ordinal}`)
        : document.getElementById(`v-${entry.ordinal}`);
    if (el) {
        el.scrollIntoView({
            behavior: appSettings.disableAnimations ? "instant" : "smooth",
            block: "center",
        });
    }
    close();
}

function close() {
    showModal.value = false;
    entries.value = [];
}
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.outline-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2pt;
}

.outline-item {
  display: flex;
  align-items: baseline;
  gap: 6pt;
  padding: 4pt 8pt;
  border-radius: 4pt;
  cursor: pointer;
  outline: none;

  &:hover, &:focus-visible {
    background-color: rgba(128, 128, 128, 0.15);
  }
}

.entry-text {
  line-height: 1.3;
}

.level-1 { font-size: 115%; font-weight: 700; }
.level-2 { font-size: 110%; font-weight: 600; }
.level-3 { font-size: 105%; font-weight: 500; }
.level-4 { font-size: 100%; }
.level-5 { font-size: 95%; }
.level-6 { font-size: 90%; }

.empty {
  text-align: center;
  opacity: 0.5;
  padding: 20pt 0;
}
</style>
