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
  <template v-if="showStrongs">
    &nbsp;<a class="skip-offset strongs" :href="link" @click.prevent="openLink"><span ref="slot"><slot/></span></a>
  </template>
</template>

<script>

import {computed, ref} from "@vue/reactivity";
import {strongsModes} from "@/composables/config";
import {inject} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {addEventFunction, EventPriorities} from "@/utils";

export default {
  name: "S",
  setup() {
    const slot = ref(null);

    const {isNewTestament} = inject("osisFragment", {})
    const letter = isNewTestament ? "G": "H";

    const link = computed(() => {
      if(slot.value === null) return null;
      const strongsNum = slot.value.innerText
      return `ab-w://?strong=${letter}${strongsNum}`
    });
    const {config, strings, ...common} = useCommon();

    const exportMode = inject("exportMode", ref(false));
    const showStrongs = computed(() => !exportMode.value && config.strongsMode !== strongsModes.off);
    function openLink(event) {
      addEventFunction(event, () => {
        if (link.value) {
          window.location.assign(link.value);
        }
      }, {priority: EventPriorities.STRONGS_LINK, icon: "custom-morph", title: strings.strongsAndMorph, dottedStrongs: false});
    }

    return {slot, openLink, link, common, showStrongs};
  },
}
</script>

<style scoped>
.strongs {
  font-size: 0.6em;
  text-decoration: none;
  color: coral;
}
</style>
