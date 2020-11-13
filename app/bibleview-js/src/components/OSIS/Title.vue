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
  <h3 :class="{isSubTitle, titleStyle: true}" v-show="show" ref="contentTag"><slot/></h3>
</template>

<script>
import TagMixin from "@/components/TagMixin";
import {useCommon} from "@/utils";

export default {
  name: "Title",
  mixins: [TagMixin],
  props: {
    type: {type: String, default: null},
    subType: {type: String, default: null},
    canonical: {type: String, default: "false"},
  },
  computed: {
    show:  ({type, subType, config, isCanonical}) =>
        config.showTitles
        && ((config.showNonCanonical && !isCanonical) || isCanonical)
        && !(type === "sub" && subType === "x-Chapter")
        && type !== "x-gen",
    isCanonical: ({canonical}) => canonical === "true",
    isSubTitle: ({type}) => type === "sub",
  },
  setup(props) {
    return useCommon(props);
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
