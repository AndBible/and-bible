<!--
  - Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <InputText ref="inputText">
    {{strings.inputReference}}
  </InputText>
  <div @click.stop class="edit-area pell">
    <div ref="editorElement"/>
    <div class="saved-notice" v-if="!dirty">&dash;{{strings.saved}}&dash;</div>
  </div>
</template>

<script>
import {inject, onBeforeUnmount, onMounted, onUnmounted, watch} from "@vue/runtime-core";
import {ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {init, exec, queryCommandState} from "@/lib/pell/pell";
import InputText from "@/components/modals/InputText";
import {
  faBible,
  faIndent,
  faListOl,
  faListUl,
  faOutdent,
  faTimes,
} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import {debounce} from "lodash";

export default {
  name: "TextEditor",
  components: {InputText},
  props: {
    text: {type: String, required: true}
  },
  emits: ['save', "close"],
  setup(props, {emit}) {
    const android = inject("android");
    const editorElement = ref(null);
    const editor = ref(null);
    const inputText = ref(null);

    // TODO: probably this hack can be removed.
    function setFocus(value) {
      android.reportInputFocus(value);
    }
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
        const originalRange = document.getSelection().getRangeAt(0);

        if(originalRange) {
          const text = await inputText.value.inputText();
          if(text !== null) {
            document.getSelection().removeAllRanges();
            if(originalRange) {
              document.getSelection().addRange(originalRange);
            }
            if(!originalRange || originalRange.collapsed) {
              exec('insertHTML', `<a href="osis://?osis=${text}">${text}</a>`);
            } else {
              exec('createLink', `osis://?osis=${text}`)
            }
          }
          editor.value.content.focus();
        }
      }
    }

    const editText = ref(props.text);
    const dirty = ref(false);

    function save() {
      if(dirty.value) {
        emit('save', editText.value)
        dirty.value = false;
      }
    }

    watch(editText, debounce(save, 2000))

    const divider = {divider: true};

    onMounted(() => {
      editor.value = init({
        element: editorElement.value,
        onChange: html => {
          editText.value = html;
          dirty.value = true;
        },
        actions: [
          'bold', 'italic', 'underline', divider, oList, uList, divider, outdent, indent, divider, bibleLink, divider, close
        ],
      });
      editor.value.content.innerHTML = editText.value;
      editor.value.content.focus();
      //android.setActionMode(false);
    });

    onBeforeUnmount(() => {
      save();
    });

    onUnmounted(() => {
      // TODO: remove setActionMode
      //android.setActionMode(true);
    })

    return {setFocus, editorElement, ...useCommon(), dirty, inputText}
  }
}
</script>
<style lang="scss">
@import '~@/lib/pell/pell.scss';
@import '~@/common.scss';

.pell-content {
  @extend .visible-scrollbar;
  max-height: calc(var(--max-height) - #{$pell-button-height} - 2*#{$pell-content-padding});
  height: inherit;
}

.pell-button {
  color: inherit;
  width: $pell-button-width *0.9;
  .night & {
    color: inherit;
  }
  &.end {
    position: absolute;
    right: 0;
    .studypad-text-entry & {
      right: 40px;
    }
  }
}

.pell-button-selected {
  background-color: rgba(0, 0, 0, 0.2);
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
  right: 0;
  bottom: 0;
  padding-inline-end: 3pt;
  color: hsla(0, 0%, 0%, 0.2);
  .night & {
    color: hsla(0, 0%, 100%, 0.2);
  }
  background: var(--background-color);
  opacity: 0.8;
  font-size: 70%;
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


</style>
