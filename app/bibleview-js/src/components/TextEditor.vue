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
  <div class="pell edit-area" ref="editorElement"/>
</template>

<script>
import {inject, onMounted, onUnmounted} from "@vue/runtime-core";
import {ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {init, exec} from "pell";
import InputText from "@/components/modals/InputText";
import {faBible, faIndent, faListOl, faListUl, faOutdent, faWindowClose} from "@fortawesome/free-solid-svg-icons";
import {icon} from "@fortawesome/fontawesome-svg-core";
import {debounce} from "lodash";

export default {
  name: "TextEditor",
  components: {InputText},
  props: {
    text: {type: String, required: true}
  },
  emits: ['changed', "close"],
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
      result: () => exec('insertOrderedList')
    };
    const uList = {
      icon: icon(faListUl).html,
      title: 'Unordered List',
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
      icon: icon(faWindowClose).html,
      title: 'Close',
      result: () => emit('close')
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

    onMounted(() => {
      editor.value = init({
        element: editorElement.value,
        onChange: debounce(html => emit('changed', html), 500),
        actions: [
          'bold', 'italic', 'underline', oList, uList, outdent, indent, bibleLink, close
        ],
      });
      editor.value.content.innerHTML = props.text;
      editor.value.content.focus();
      //android.setActionMode(false);
    });

    onUnmounted(() => {
      // TODO: remove setActionMode
      //android.setActionMode(true);
    })

    const editText = ref(props.text);

    return {setFocus, editorElement, ...useCommon(), editText, inputText}
  }
}
</script>
<style lang="scss">
@import '~pell/src/pell.scss';
.pell-content {
  height: 65pt;
  max-height: calc(var(--max-height) - #{$pell-button-height} - 2*#{$pell-content-padding});
}

.pell-button {
  color: inherit;

  .night & {
    color: inherit;
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
  .night & {
    color: rgba(255, 255, 255, 0.5);
  }
}

</style>
<style scoped lang="scss">
.edit-area {
  width: 100%;
//  .night & {
//    background-color: black;
//    color: white;
//  }
}


</style>
