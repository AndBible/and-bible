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
  <div ref="fragElement">
    <transition-group name="fade">
      <div v-for="{key, template} in templates" :key="key">
        <OsisSegment :osis-template="template" />
      </div>
    </transition-group>
    <FeaturesLink :fragment="fragment"/>
  </div>
</template>

<script>
import {reactive, ref} from "@vue/reactivity";
import {useStrings} from "@/composables";
import {AutoSleep, osisToTemplateString} from "@/utils";
import OsisSegment from "@/components/documents/OsisSegment";
import FeaturesLink from "@/components/FeaturesLink";

const parser = new DOMParser();

export default {
  name: "OsisFragment",
  props: {
    showTransition: {type: Boolean, default: false},
    fragment: {type: Object, required: true},
  },
  components: {FeaturesLink, OsisSegment},
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {
      xml,
      key: fragmentKey,
    } = props.fragment;

    const fragmentReady = ref(!props.showTransition);
    const strings = useStrings();
    const fragElement = ref(null);

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
    return {templates, strings, fragElement}
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
