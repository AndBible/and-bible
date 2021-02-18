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
    <template v-for="(fragment,idx) in osisFragments" :key="fragment.key">
      <OsisFragment :fragment="fragment" :show-transition="document.showTransition"/>
      <div v-if="osisFragments.length > 1 && idx < osisFragments.length" class="divider" />
    </template>
</template>

<script>
import OsisFragment from "@/components/documents/OsisFragment";
import {inject, onMounted, onUnmounted} from "@vue/runtime-core";

export default {
  name: "OsisDocument",
  components: {OsisFragment},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure,no-unused-vars
    const {id, type, osisFragments, bookInitials, bookName, key} = props.document;

    const {addCss, removeCss} = inject("customCss");
    onMounted(() => {
      addCss(bookInitials);
    });
    onUnmounted(() => {
      removeCss(bookInitials);
    });

    return {osisFragments};
  }
}
</script>
