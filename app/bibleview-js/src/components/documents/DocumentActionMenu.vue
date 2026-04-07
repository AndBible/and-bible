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
  <div v-if="expanded" ref="menuEl" class="document-action-menu" :style="menuStyle"
       @click.stop @click.capture="onMenuClick" @touchstart.stop @touchend.stop>
    <div class="action-buttons">
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
      <div class="journal-button" @click.stop="close">
        <FontAwesomeIcon :icon="faTimes"/>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {inject, nextTick, onBeforeUnmount, ref, watch} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faCopy, faEdit, faShareAlt, faSync, faTimes, faTrash} from "@fortawesome/free-solid-svg-icons";
import {androidKey} from "@/types/constants";
import {emit, eventBus} from "@/eventbus";
import WholePageBookmarks from "@/components/WholePageBookmarks.vue";
import type {OsisDocument} from "@/types/documents";

defineProps<{
  document: OsisDocument
}>();

const android = inject(androidKey)!;

const expanded = ref(false);
const menuEl = ref<HTMLElement | null>(null);
const anchorRect = ref<DOMRect | null>(null);

const menuStyle = ref<Record<string, string>>({});

function updateMenuPosition() {
    if (!anchorRect.value) return;
    menuStyle.value = {
        top: `${anchorRect.value.bottom + window.scrollY}px`,
        left: `${anchorRect.value.left + window.scrollX}px`,
    };
}

async function clampToViewport() {
    await nextTick();
    const el = menuEl.value;
    if (!el || !anchorRect.value) return;
    const rect = el.getBoundingClientRect();
    const vw = globalThis.document.documentElement.clientWidth;

    if (rect.right > vw) {
        menuStyle.value = {
            ...menuStyle.value,
            left: `${Math.max(0, vw - rect.width)}px`,
        };
    }
    if (rect.left < 0) {
        menuStyle.value = {
            ...menuStyle.value,
            left: '0px',
        };
    }
}

function close() {
    expanded.value = false;
}

async function openMenu(anchorEl: HTMLElement) {
    anchorRect.value = anchorEl.getBoundingClientRect();
    expanded.value = true;
    updateMenuPosition();
    await clampToViewport();
}

function onMenuClick() {
    close();
}

function onDocumentClick(e: Event) {
    if (menuEl.value && !menuEl.value.contains(e.target as Node)) {
        close();
    }
}

watch(expanded, v => {
    if (v) {
        eventBus.on("back_clicked", close);
        eventBus.on("bookmark_clicked", close);
        // Delay adding the listener so the current click/touch cycle doesn't immediately close
        setTimeout(() => {
            globalThis.document.addEventListener('click', onDocumentClick, true);
            globalThis.document.addEventListener('touchend', onDocumentClick, true);
        }, 0);
    } else {
        eventBus.off("back_clicked", close);
        eventBus.off("bookmark_clicked", close);
        globalThis.document.removeEventListener('click', onDocumentClick, true);
        globalThis.document.removeEventListener('touchend', onDocumentClick, true);
    }
});

onBeforeUnmount(() => {
    globalThis.document.removeEventListener('click', onDocumentClick, true);
    globalThis.document.removeEventListener('touchend', onDocumentClick, true);
    eventBus.off("back_clicked", close);
    eventBus.off("bookmark_clicked", close);
});

defineExpose({openMenu});
</script>

<style scoped lang="scss">
.document-action-menu {
  position: absolute;
  z-index: 20;
}

.action-buttons {
  display: flex;
  background: var(--background-color);
  border: 1pt solid rgba(0, 0, 0, 0.3);
  border-radius: 10pt;
  opacity: 0.9;

  .night & {
    border-color: rgba(255, 255, 255, 0.6);
  }
  .monochrome & {
    border-color: black;
    opacity: 1;
  }
  .monochrome.night & {
    border-color: white;
  }
}
</style>
