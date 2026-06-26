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
  <div :style="parentStyle" class="editable-text">
    <div class="editor-container" :class="{constraintDisplayHeight}" v-if="editMode">
      <MarkdownEditor v-if="isMarkdown" :text="editText || ''" :note-editor-context="noteEditorContext" :content-type-name="isMarkdown ? 'MARKDOWN' : 'HTML'" @save="textChanged" @close="editMode = false"/>
      <HtmlEditor v-else :text="editText || ''" :note-editor-context="noteEditorContext" :content-type-name="isMarkdown ? 'MARKDOWN' : 'HTML'" @save="textChanged" @close="editMode = false"/>
    </div>
    <template v-else>
      <div v-if="editText" class="notes-display" :class="[{constraintDisplayHeight}, isMarkdown ? 'markdown-notes' : '']" @click="handleClicks">
        <div v-html="displayHtml"/>
      </div>
      <div class="placeholder" v-else-if="showPlaceholder" @click="handleClicks">
        <slot>
          {{ strings.editTextPlaceholder }}
        </slot>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
export interface NoteEditorContext {
    entityType: string
    entityId: string
}

let cancelOpen = () => {}
</script>

<script lang="ts" setup>
import {computed, inject, ref, watch} from "vue";
import HtmlEditor from "@/components/HtmlEditor.vue";
import MarkdownEditor from "@/components/MarkdownEditor.vue";
import {useCommon} from "@/composables";
import {appSettingsKey, exportModeKey} from "@/types/constants";
import {Nullable} from "@/types/common";
import {TextContentType} from "@/types/client-objects";
import {Marked} from "marked";
import DOMPurify from "dompurify";
import {PURIFY_CONFIG} from "@/composables/slot-html-content";

const markdownParser = new Marked({breaks: true, gfm: true});

const emit = defineEmits(["closed", "save", "opened"]);
const props = withDefaults(defineProps<{
    editDirectly?: boolean
    showPlaceholder?: boolean
    text: Nullable<string>
    contentType?: Nullable<TextContentType>
    maxEditorHeight?: string
    constraintDisplayHeight?: boolean
    disableClickToEdit?: boolean
    noteEditorContext?: NoteEditorContext | null
}>(), {
    editDirectly: false,
    showPlaceholder: false,
    text: null,
    contentType: null,
    maxEditorHeight: "inherit",
    constraintDisplayHeight: false,
    disableClickToEdit: false,
    noteEditorContext: null
})

const appSettings = inject(appSettingsKey)!;
const isMarkdown = computed(() =>
    props.contentType === "MARKDOWN" ||
    (props.contentType == null && appSettings.notesContentType === "MARKDOWN")
);

const displayHtml = computed(() => {
    if (!editText.value) return "";
    if (isMarkdown.value) {
        return DOMPurify.sanitize(markdownParser.parse(editText.value) as string, PURIFY_CONFIG);
    }
    return editText.value;
});

const editMode = ref<boolean>(props.editDirectly);
const parentStyle = ref(`--max-height: ${props.maxEditorHeight}; font-family: var(--font-family); font-size: var(--font-size);`);
const editText = ref(props.text);
const exportMode = inject(exportModeKey, ref(false));

function cancelFunc() {
    editMode.value = false;
}

watch(editMode, (mode, oldValue) => {
    if (!mode) {
        emit("closed", editText.value);
    }
    else {
        emit("opened")
        if (cancelFunc !== cancelOpen) {
            cancelOpen()
        }
        cancelOpen = cancelFunc
    }
}, {immediate: true})
watch(() => props.text, t => {
    editText.value = t;
})

watch(exportMode, mode => {
    if (mode) {
        editMode.value = false;
    }
});

function textChanged(newText: string) {
    editText.value = newText
    emit("save", newText);
}

// AndBible internal URLs in "scheme://..." form — routed through the WebView's
// shouldOverrideUrlLoading (via window.location.assign) so that openLink(uri)
// parses query-parameter URLs like "osis://?osis=Gen.1.1&v11n=KJV". The simple
// scheme:key form (e.g. "osis:Gen.1.1" from AI content) keeps using
// openExternalLink, which routes via linkControl.loadApplicationUrl.
const INTERNAL_SCHEMES = /^(?:sword|osis|strongs|morphology|my-notes|journal|ab-w|ab-find-all|ab-error|epub-ref|multi|download):\/\//i;

function handleClicks(event: MouseEvent) {
    const link = (event.target as HTMLElement).closest("a") as HTMLAnchorElement | null;
    if (link) {
        event.preventDefault();
        event.stopPropagation();
        const href = link.getAttribute("href");
        if (href) {
            if (INTERNAL_SCHEMES.test(href)) {
                window.location.assign(href);
            } else {
                window.android.openExternalLink(href);
            }
        }
        return;
    }
    if (!props.disableClickToEdit) {
        editMode.value = true;
    }
}

const {strings} = useCommon();
defineExpose({editMode});
</script>

<style lang="scss" scoped>
@use "@/lib/pell/pell.scss";
@use "@/common.scss" as *;

.notes-display {
  //  width: 100%;
  margin-bottom: 8pt;
  padding: 1px 7px 10px 7px;

  &.constraintDisplayHeight {
    @extend .visible-scrollbar;
    overflow-y: auto;
    // Keep scrolling of a long note inside the display area; don't chain to the
    // Bible document behind the (non-blocking) modal.
    overscroll-behavior: contain;
    max-height: calc(var(--max-height) - 17px);
  }
}

.placeholder {
  opacity: 0.5;
  .monochrome & {
    opacity: 1;
  }
}

.editor-container {
  max-width: 100%;
  padding-top: 8pt;
  padding-bottom: 3pt;
  padding-inline-start: 0;

  &.constraintDisplayHeight {
    padding-top: 0;
    padding-bottom: 0;
    // Bound the editor height and make it scroll within the modal. Without this the
    // auto-growing textarea overflows the (non-blocking) modal, so touch/scroll gestures
    // fall through to the Bible document behind it (the note "scrolls the Bible instead").
    // overscroll-behavior: contain additionally stops scroll chaining to the background.
    max-height: var(--max-height);
    overflow-y: auto;
    overscroll-behavior: contain;
  }
}

.edit-button {
  @extend .journal-button;
  position: absolute;
  height: 20pt;
  width: 20pt;

  [dir=ltr] & {
    right: 0;
  }

  [dir=rtl] & {
    left: 0;
  }

  top: 0;
}

.editable-text {
  position: relative;
  color: var(--text-color);
  background-color: var(--background-color);
}
</style>
<style lang="scss">
@use "@/lib/markdown-render" as md;

div.pell-content, .pell-content div, .notes-display:not(.markdown-notes) div {
  margin-top: 5px;
}

.editable-text ul, ol, blockquote {
  margin-top: 5pt;
  margin-bottom: 5pt;
  margin-left: 0 !important;
  padding-left: 15pt !important;

  & ul, ol {
    margin-top: 0;
    margin-bottom: 0;
  }
}

.editable-text ul {
  padding-left: 12pt !important;
}

.editable-text .placeholder {
  padding: 15px;
}

.markdown-notes {
  @include md.markdown-content;
  // Override shared padding with !important for EditableText context
  ul, ol { padding-left: 1.5em !important; }
  blockquote { padding-left: 1em !important; }
}

.night .markdown-notes {
  @include md.markdown-content-night;
}

.monochrome .markdown-notes {
  @include md.markdown-content-monochrome;
}

.monochrome.night .markdown-notes {
  @include md.markdown-content-monochrome-night;
}
</style>
