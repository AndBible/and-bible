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
  <a :class="{reference: true, clicked, 'last-clicked': lastClicked}" @click="openLink($event, link)" :href="link" ref="content"><slot/></a>
</template>

<script>
import {checkUnsupportedProps, useCommon} from "@/composables";
import {addEventFunction} from "@/utils";
import {ref} from "@vue/reactivity";

let cancelFunc = () => {};

export default {
  name: "Reference",
  props: {
    osisRef: {type: String, default: null},
    source: {type: String, default: null},
    type: {type: String, default: null},
  },
  computed: {
    queryParams() {
      if(!this.osisRef && this.$refs.content) {
        return "content=" + encodeURI(this.$refs.content.innerText);
      }
      return "osis=" + encodeURI(this.osisRef)
    },
    link({queryParams}) {
      return `osis://?${queryParams}`
    }
  },
  setup(props) {
    checkUnsupportedProps(props, "type");
    const clicked = ref(false);
    const lastClicked = ref(false);
    const {strings, ...common} = useCommon();
    function openLink(event, url) {
      addEventFunction(event, () => {
        window.location.assign(url)
        cancelFunc();
        clicked.value = true;
        lastClicked.value = true;
        cancelFunc = () => lastClicked.value = false;
      }, {title: strings.referenceLink});
    }
    return {openLink, clicked, lastClicked, ...common};
  },
}

</script>

<style lang="scss" scoped>
a {
  &.clicked {
    color: green;
  }
  &.last-clicked {
    color: red;
  }
  .night & {
    &.clicked {
      color: #73d573;
    }

    &.last-clicked {
      color: #de6e6e;
    }
  }
}

</style>
