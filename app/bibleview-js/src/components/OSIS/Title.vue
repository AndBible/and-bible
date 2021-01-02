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
  <h3 :class="{isSubTitle, titleStyle: true}" v-show="show"><slot/></h3>
</template>

<script>
import {useCommon} from "@/composables";

export default {
  name: "Title",
  props: {
    type: {type: String, default: null},
    subType: {type: String, default: null},
    canonical: {type: String, default: "false"},
    short: {type: String, default: null},
  },
  computed: {
    show:  ({type, subType, config, isCanonical}) =>
        config.showSectionTitles
        && ((config.showNonCanonical && !isCanonical) || isCanonical)
        && !(type === "sub" && subType === "x-Chapter")
        && type !== "x-gen",
    isCanonical: ({canonical}) => canonical === "true",
    isSubTitle: ({type}) => type === "sub",
  },
  setup() {
    return useCommon();
  },
}
</script>

<style scoped type="text/scss">
.listStyle .titleStyle {
  margin-inline-start: -1em;
}

h3.isSubTitle {
  font-size: 110%;
  margin-inline-start: 1em;
}
</style>
