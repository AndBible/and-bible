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
  <div ref="fragElement">
    <transition-group name="fade">
      <div class="inlineDiv" v-for="{key, template} in templates" :key="key">
        <OsisSegment :osis-template="template" />
      </div>
    </transition-group>
    <div class="features-link" v-if="featuresLink">
      <a :href="featuresLink">{{strings.findAllOccurrences}}</a>
    </div>
  </div>
</template>

<script>
import {inject, provide} from "@vue/runtime-core";
import {useBookmarks} from "@/composables/bookmarks";
import {reactive, ref} from "@vue/reactivity";
import OsisSegment from "@/components/OsisSegment";
import {AutoSleep} from "@/utils";
import {usePoetic, useStrings} from "@/composables";

export default {
  name: "OsisFragment",
  components: {OsisSegment},
  props: {
    data: {type: Object, required: true},
    showTransition: {type: Boolean, default: false},
  },
  setup(props) {
    // Props for this component is considered to be read-only.
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {
      xml,
      key: fragmentKey,
      ordinalRange = null,
      features: {type: featureType = null, keyName: featureKeyName = null} = {}} = props.data;
    const fragmentReady = ref(!props.showTransition);
    const strings = useStrings();
    const fragElement = ref(null);

    // TODO: check if these are used
    const [book, osisID] = fragmentKey.split("--");

    const globalBookmarks = inject("globalBookmarks");
    const config = inject("config");

    // Disable bookmarks for non-bible documents
    if(ordinalRange) {
      // To remove verse 0 from ordinalRange (is always included)
      const realOrdinalRange = ordinalRange ? [ordinalRange[0]+1, ordinalRange[1]]: null;
      useBookmarks(fragmentKey, realOrdinalRange, globalBookmarks, book, fragmentReady, config);
    }
    usePoetic(fragElement, fragmentReady);
    provide("fragmentInfo", {fragmentKey, book, osisID});

    const template = xml
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
    // TODO: leaving this now for a later point. Need to re-design replace_osis + setup_content for this too.
    if(false && props.showTransition) {
      populate();
    } else {
      templates.push({template, key: `${fragmentKey}-0`})
    }
    const featuresLink = featureType ?`ab-find-all://?type=${featureType}&name=${featureKeyName}`: null;
    return {templates, featuresLink, strings, fragElement}
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
.features-link {
  padding-top: 1em;
  text-align: right;
}
</style>
