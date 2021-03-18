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
    <transition-group name="fade">
      <div v-for="{key, template} in templates" :key="key">
        <OsisSegment :osis-template="template" />
      </div>
    </transition-group>
    <OpenAllLink/>
    <FeaturesLink :fragment="fragment"/>
  </div>
</template>

<script>
import {computed, reactive, ref} from "@vue/reactivity";
import {inject, onMounted, provide} from "@vue/runtime-core";
import {useCommon, useReferenceCollector} from "@/composables";
import {AutoSleep, highlightVerseRange, osisToTemplateString} from "@/utils";
import OsisSegment from "@/components/documents/OsisSegment";
import FeaturesLink from "@/components/FeaturesLink";
import {BookCategories} from "@/constants";
import OpenAllLink from "@/components/OpenAllLink";
import {useStrings} from "@/composables/strings";

const parser = new DOMParser();

export default {
  name: "OsisFragment",
  props: {
    showTransition: {type: Boolean, default: false},
    fragment: {type: Object, required: true},
    highlightOrdinalRange: {type: Array, default: null},
    highlightOffsetRange: {type: Array, default: null},
    hideTitles: {type: Boolean, default: false}
  },
  components: {OpenAllLink, FeaturesLink, OsisSegment},
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {
      xml,
      key: fragmentKey,
      bookCategory,
      bookInitials,
      osisRef,
    } = props.fragment;
    const uniqueId = ref(Date.now().toString());
    const {config} = useCommon()
    const customConfig = computed(() => {
      const changes = {};
      if(props.hideTitles) {
        changes.showSectionTitles = false;
      }
      return {...config, ...changes};
    });
    provide("config", customConfig);

    const fragmentReady = ref(!props.showTransition);
    const strings = useStrings();
    provide("osisFragment", props.fragment)
    const {registerBook} = inject("customCss");
    registerBook(bookInitials);

    const referenceCollector = useReferenceCollector();

    if(bookCategory === BookCategories.COMMENTARIES) {
      provide("referenceCollector", referenceCollector);
    }
    onMounted(() => {
      if(props.highlightOrdinalRange && props.highlightOffsetRange) {
        try {
          highlightVerseRange(`#frag-${uniqueId.value}`, props.highlightOrdinalRange, props.highlightOffsetRange);
        } catch (e) {
          console.error("Highlight failed for ", osisRef);
        }
      }
    });

    const template = osisToTemplateString(xml)
    const templates = reactive([]);

    async function populate() {
      const autoSleep = new AutoSleep();
      const xmlDoc = parser.parseFromString(template, "text/xml");
      let ordinalCount = 0;

      for (const c of xmlDoc.firstElementChild.children) {
        const key = `${fragmentKey.value}-${ordinalCount++}`;
        templates.push({template: c.outerHTML, key})
        await autoSleep.autoSleep();
      }
      fragmentReady.value = true;
    }
    // TODO: leaving this now for a later point. Need to re-design replace_osis + setup_content for this too.
    if(false && props.showTransition) {
      populate();
    } else {
      templates.push({template, key: `${fragmentKey}-0`})
    }
    return {templates, strings, uniqueId}
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
