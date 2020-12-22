<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
  <transition-group v-if="showTransition" name="fade">
    <div class="inlineDiv" v-for="{key, template} in templates" :key="key">
      <OsisSegment :osis-template="template" />
    </div>
  </transition-group>
  <OsisSegment v-else :osis-template="templates[0].template" />
</template>

<script>
import {inject, provide} from "@vue/runtime-core";
import {useBookmarks} from "@/composables/bookmarks";
import {computed, reactive, ref} from "@vue/reactivity";
import OsisSegment from "@/components/OsisSegment";
import {AutoSleep} from "@/utils";

export default {
  name: "OsisFragment",
  components: {OsisSegment},
  props: {
    xml: {type: String, required: true},
    fragmentKey: {type: String, required: true},
    ordinalRange: {type: Array, default: null},
    showTransition: {type: Boolean, default: true},
  },
  setup(props) {
    const fragmentKey = computed(() => props.fragmentKey);
    const ordinalRange = computed(() => props.ordinalRange);
    const fragmentReady = ref(!props.showTransition);
    // TODO: check if these are used
    const [book, osisID] = props.fragmentKey.split("--");

    const globalBookmarks = inject("globalBookmarks");

    useBookmarks(fragmentKey, ordinalRange, globalBookmarks, book, fragmentReady);
    provide("fragmentInfo", {fragmentKey, book, osisID});

    const template = props.xml
        .replace(/(<\/?)(\w)(\w*)([^>]*>)/g,
            (m, tagStart, tagFirst, tagRest, tagEnd) =>
                `${tagStart}Osis${tagFirst.toUpperCase()}${tagRest}${tagEnd}`);

    const templates = reactive([]);

    async function populate() {
      const autoSleep = new AutoSleep();
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(template, "text/xml");
      let ordinalCount = 0;

      for (const c of xmlDoc.firstElementChild.children) {
        const key = `${fragmentKey.value}-${ordinalCount++}`;
        templates.push({template: c.outerHTML, key})
        await autoSleep.autoSleep();
      }
      fragmentReady.value = true;
    }

    if(props.showTransition) {
      populate();
    } else {
      templates.push({template, key: `${fragmentKey}-0`})
    }

    return {templates}
  }
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.5s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0
}
</style>
