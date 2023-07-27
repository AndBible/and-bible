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
  <ModalDialog v-if="showHelp" @close="showHelp = false" blocking locate-top>
    {{ sprintf(strings.refParserHelp, "RefParser") }}
    <a @click="openDownloads">{{ strings.openDownloads }}</a>
    <template #title>
      {{ strings.inputReference }}
    </template>
  </ModalDialog>
  <InputText ref="inputText">
    {{ strings.inputReference }}
    <template #buttons>
      <button v-if="!hasRefParser" class="modal-action-button right" @touchstart.stop @click="showHelp = !showHelp">
        <FontAwesomeIcon icon="question-circle"/>
      </button>
      <button class="modal-action-button right" @touchstart.stop @click="refChooserDialog">
        <FontAwesomeIcon icon="hand-pointer"/>
      </button>
    </template>
  </InputText>
  <div @click.stop class="edit-area pell">
    <div ref="editorElement"/>
    <div class="saved-notice" v-if="!dirty">
      <FontAwesomeIcon icon="save"/>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject, onBeforeUnmount, onMounted, onUnmounted, ref, watch} from "vue";
import {useCommon} from "@/composables";
import {exec, init, queryCommandState} from "@/lib/pell/pell";
import InputText from "@/components/modals/InputText.vue";
import {useStrings} from "@/composables/strings";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faBible, faIndent, faListOl, faListUl, faOutdent, faTimes,} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import {debounce} from "lodash";
import ModalDialog from "@/components/modals/ModalDialog.vue";
import {setupElementEventListener} from "@/utils";
import {androidKey, customFeaturesKey, keyboardKey} from "@/types/constants";

const props = defineProps<{ text: string }>();
const emit = defineEmits(["save", "close"]);

const android = inject(androidKey)!;
const {parse, features} = inject(customFeaturesKey)!
const {disableKeybindings} = inject(keyboardKey)!;
const hasRefParser = computed(() => features.has("RefParser"));
const editorElement = ref<HTMLElement | null>(null);

type EditorElement = HTMLElement & { content: HTMLElement }

const editor = ref<EditorElement | null>(null);
const inputText = ref<InstanceType<typeof InputText> | null>(null);
const strings = useStrings();
const showHelp = ref(false);

const oList = {
    icon: icon(faListOl).html,
    title: 'Ordered List',
    state: () => queryCommandState('insertOrderedList'),
    result: () => exec('insertOrderedList')
};

const uList = {
    icon: icon(faListUl).html,
    title: 'Unordered List',
    state: () => queryCommandState('insertUnorderedList'),
    result: () => exec('insertUnorderedList')
}

const indent = {
    icon: icon(faIndent).html,
    title: 'Indent',
    result: () => exec('indent')
}

const outdent = {
    icon: icon(faOutdent).html,
    title: 'Indent',
    result: () => exec('outdent')
}

const close = {
    icon: icon(faTimes).html,
    class: "end",
    title: 'Close',
    result: () => {
        save();
        emit('close')
    }
}

const bibleLink = {
    name: 'bibleLink',
    icon: icon(faBible).html,
    title: 'Insert bible reference',
    result: async () => {
        const originalRange = document.getSelection()?.getRangeAt(0);
        //Always add the "en" language, so at least the english parser always exists.
        let error = ""

        if (originalRange) {
            let text: string | null = originalRange.toString();
            let parsed = "";
            //Keep trying to get a bible reference until either
            //    * It is successfully parsed (parsed != "") or
            //    * The user cancels (text === null)
            while (parsed === "" && text !== null) {
                text = await inputText.value!.inputText(text, error);
                if (text !== null) {
                    parsed = parse(text)
                    if (parsed === "") {
                        error = strings.invalidReference;
                    } else {
                        error = "";
                    }
                }
            }
            if (text !== null) {
                document.getSelection()!.removeAllRanges();
                if (originalRange) {
                    document.getSelection()!.addRange(originalRange);
                }
                if (!originalRange || originalRange.collapsed) {
                    exec('insertHTML', `<a href="osis://?osis=${parsed}">${text}</a>`);
                } else {
                    exec('createLink', `osis://?osis=${parsed}`)
                }
            }
            editor.value!.content.focus();
        }
    }
}

const editText = ref(props.text);
const dirty = ref(false);

function save() {
    if (dirty.value) {
        emit('save', editText.value)
        dirty.value = false;
    }
}

watch(editText, debounce(save, 2000))

const divider = {divider: true};

function openDownloads() {
    showHelp.value = false;
    android.openDownloads();
}

async function refChooserDialog() {
    inputText.value!.setText(await android.refChooserDialog());
}

onMounted(() => {
    editor.value = init({
        element: editorElement.value!,
        onChange: (html: string) => {
            editText.value = html;
            dirty.value = true;
        },
        actions: [
            'bold', 'italic', 'underline', divider, oList, uList, divider, outdent, indent, divider, bibleLink, divider, close
        ],
    });
    editor.value!.content.innerHTML = editText.value;
    editor.value!.content.focus();
    android.setEditing(true);
    disableKeybindings.value ++;
});

setupElementEventListener(editorElement, "keyup", e => {
    if (e.key === "Escape") {
        save();
        emit('close')
        e.stopPropagation()
    }
})

onBeforeUnmount(() => {
    save();
});

onUnmounted(() => {
    android.setEditing(false);
    disableKeybindings.value --;
})

const {sprintf} = useCommon();
</script>
<style lang="scss">
@import '~@/lib/pell/pell.scss';
@import '~@/common.scss';

.pell-content {
  @extend .visible-scrollbar;
  max-height: calc(var(--max-height) - #{$pell-button-height} - 2 * #{$pell-content-padding});
  height: inherit;
  padding: 0 7px 5px 7px;
  z-index: 1;
  position: relative;
}

.pell-button {
  color: inherit;
  width: $pell-button-width *0.9;
  height: $pell-button-height *0.9;
  margin: 0 1px 0 1px;

  .night & {
    color: inherit;
  }

  &.end {
    position: absolute;

    [dir=ltr] & {
      right: 0;
    }

    [dir=rtl] & {
      left: 0;
    }

    //.studypad-text-entry & {
    //  [dir=ltr] & {
    //    right: 40px;
    //  }
    //  [dir=rtl] & {
    //    left: 40px;
    //  }
    //}
  }
}

.pell-button-selected {
  background-color: rgba(0, 0, 0, 0.2);
  border-radius: 5px;

  .night & {
    background-color: rgba(255, 255, 255, 0.2);
  }
}

.pell-actionbar {
  background-color: inherit;
  color: rgba(0, 0, 0, 0.6);

  .night & {
    color: rgba(255, 255, 255, 0.5);
  }
}

.saved-notice {
  position: absolute;
  right: 5px;
  bottom: $pell-button-height;
  padding-inline-end: 3pt;
  color: hsla(112, 40%, 33%, 0.8);

  .night & {
    color: hsla(112, 40%, 33%, 0.8);
  }

  opacity: 0.8;
  font-size: 10px;
  z-index: 0;
}

.pell-divider {
  background-color: hsla(0, 0%, 0%, 0.2);

  .night & {
    background-color: hsla(0, 0%, 100%, 0.2);
  }
}

.edit-area {
  width: 100%;
  position: relative;
}

.edit-area, .pell {
  margin: 0;
}

.header {
  display: flex;
  justify-content: space-between;
  width: 100%;
}


</style>
