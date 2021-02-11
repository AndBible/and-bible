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
  <div v-for="(fragment, index) in osisFragments" :key="index">
    <div class="ref-link">
      <a :href="link(fragment)">{{fragment.keyName}}</a>
    </div>
    <OsisFragment :fragment="fragment"/>
    <div v-if="index < osisFragments.length - 1" class="separator"/>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment";

export default {
  name: "MultiDocument",
  components: {OsisFragment},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    const {osisFragments} = props.document;
    function link(frag) {
      const osis = encodeURI(`${frag.bookInitials}:${frag.osisRef}`)
      return `osis://?osis=${osis}`
    }

    return {osisFragments, link, ...useCommon()}
  }
}
</script>

<style scoped>
.ref-link {
  padding-bottom: 1em;
  font-weight: bold;
}
.separator {
  height: 1pt;
  width: calc(100% - 10pt);
  margin: 10pt 5pt 5pt;
  background: rgba(0, 0, 0, 0.2);
}

</style>
