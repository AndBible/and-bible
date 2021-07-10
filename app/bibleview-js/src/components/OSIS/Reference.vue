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
  <a class="reference" :class="{clicked, 'last-clicked': lastClicked}" @click.prevent="openLink($event, link)" :href="link" ref="content"><slot/></a>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {addEventFunction, EventPriorities, sleep} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";
import {fadeReferenceDelay} from "@/constants";

let cancelFunc = () => {};

export default {
  name: "Reference",
  props: {
    osisRef: {type: String, default: null},
    source: {type: String, default: null},
    type: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "type");
    const clicked = ref(false);
    const lastClicked = ref(false);
    const {strings, ...common} = useCommon();
    const referenceCollector = inject("referenceCollector", null);
    const content = ref(null);
    const osisFragment = inject("osisFragment");

    const osisRef = computed(() => {
      if(!props.osisRef && content.value) {
        return content.value.innerText;
      }
      return props.osisRef;
    });

    const queryParams = computed(() => {
      let paramString = "osis=" + encodeURI(osisRef.value)
      if(osisFragment.v11n) {
        paramString += "&v11n=" + encodeURI(osisFragment.v11n)
      }
      return paramString
    })

    const link = computed(() => {
      return `osis://?${queryParams.value}`
    });

    if(referenceCollector) {
      referenceCollector.collect(osisRef);
    }

    const {registerEndHighlight} = inject("verseHighlight");

    function openLink(event, url) {
      addEventFunction(event, () => {
        window.location.assign(url)
        cancelFunc();
        clicked.value = true;
        lastClicked.value = true;
        cancelFunc = () => lastClicked.value = false;
        registerEndHighlight(cancelFunc);
        sleep(fadeReferenceDelay).then(() => cancelFunc());
      }, {title: strings.referenceLink, priority: EventPriorities.REFERENCE});
    }
    return {openLink, clicked, lastClicked, content, link, ...common};
  },
}

</script>

<style lang="scss" scoped>
.reference {
  transition: background-color 1s ease;
  border-radius: 5pt;
}
a {
  &.clicked {
    color: #8b00ee;
  }
  &.last-clicked {
    background-color: rgba(255, 230, 0, 0.4);
  }
  .night & {
    &.clicked {
      color: #8b00ee;
    }
    &.last-clicked {
      background-color: rgba(255, 230, 0, 0.6);
    }
  }
}

</style>
