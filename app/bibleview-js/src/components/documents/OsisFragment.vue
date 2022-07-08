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
  <div :id="`frag-${uniqueId}`" :class="`sword-${fragment.bookInitials}`" :lang="fragment.language" :dir="fragment.direction" >
    <OsisSegment :osis-template="template" />
  </div>
</template>

<script>
import {computed, ref} from "vue";
import {inject, onMounted, provide, watch} from "vue";
import {highlightVerseRange, osisToTemplateString} from "@/utils";
import OsisSegment from "@/components/documents/OsisSegment";
import {useStrings} from "@/composables/strings";
import {useCommon} from "@/composables";

export default {
  name: "OsisFragment",
  props: {
    fragment: {type: Object, required: true},
    highlightOrdinalRange: {type: Array, default: null},
    highlightOffsetRange: {type: Array, default: null},
    hideTitles: {type: Boolean, default: false},
    doNotConvert: {type: Boolean, default: false},
  },
  components: {OsisSegment},
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {
      bookInitials,
      osisRef,
    } = props.fragment;
    const uniqueId = ref(Date.now().toString());

    if(props.hideTitles) {
      provide("hideTitles", true);
    }

    const strings = useStrings();
    provide("osisFragment", props.fragment)
    const {registerBook} = inject("customCss");
    registerBook(bookInitials);

    let undo = () => {};
    function refreshHighlight() {
      undo();
      if(props.highlightOrdinalRange && props.highlightOffsetRange) {
        try {
          undo = highlightVerseRange(`#frag-${uniqueId.value}`, props.highlightOrdinalRange, props.highlightOffsetRange);
        } catch (e) {
          console.error("Highlight failed for ", osisRef);
        }
      }
    }

    onMounted(() => {
      refreshHighlight();
    });

    const template = computed(() => {
      const xml = props.fragment.xml;
      return !props.doNotConvert ? osisToTemplateString(xml) : xml;
    });

    watch(props, () => refreshHighlight());
    return {template, strings, uniqueId, ...useCommon()}
  }
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.1s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0
}
</style>
<style lang="scss">
.highlight {
  font-weight: bold;
  /*
  background-color: rgba(130, 130, 130, 0.2);
  .night & {
    background-color: rgba(168, 165, 165, 0.7);
  }
   */
}
</style>
