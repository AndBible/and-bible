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
  <a class="reference" :class="{clicked, isHighlighted}" @click.prevent="openLink($event, link)" :href="link" ref="content"><span ref="slot"><slot/></span><template v-if="slotEmpty">{{osisRef}}&nbsp;</template></a>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {addEventFunction, EventPriorities} from "@/utils";
import {computed, ref} from "@vue/reactivity";
import {inject} from "@vue/runtime-core";

export default {
  name: "Reference",
  props: {
    osisRef: {type: String, default: null},
    target: {type: String, default: null},
    source: {type: String, default: null},
    type: {type: String, default: null},
  },
  setup(props) {
    checkUnsupportedProps(props, "type");
    const clicked = ref(false);
    const isHighlighted = ref(false);
    const {strings, ...common} = useCommon();
    const {addCustom, resetHighlights} = inject("verseHighlight");
    const referenceCollector = inject("referenceCollector", null);
    const content = ref(null);
    const osisFragment = inject("osisFragment");
    const slot = ref(null);
    const slotEmpty = computed(() => {
      if(slot.value === null) return true;
      return slot.value.innerText.trim() === "";
    });

    const osisRef = computed(() => {
      if((!props.osisRef && !props.target) && content.value) {
        return `${osisFragment.bookInitials}:${content.value.innerText}`;
      } else if(props.target) {
        return props.target;
      } else {
        return props.osisRef;
      }
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

    function openLink(event, url) {
      addEventFunction(event, () => {
        window.location.assign(url)
        clicked.value = true;
        resetHighlights();
        isHighlighted.value = true;
        addCustom(() => isHighlighted.value = false);
      }, {title: strings.referenceLink, priority: EventPriorities.REFERENCE});
    }
    return {openLink, clicked, isHighlighted, content, link, slot, slotEmpty, ...common};
  },
}

</script>

<style lang="scss" scoped>
@import "~@/common.scss";
.reference {
  @extend .highlight-transition;
  border-radius: 5pt;
}
a {
  &.clicked {
    color: #8b00ee;
  }
  .night & {
    &.clicked {
      color: #bf7eff;
    }
  }
}

</style>
