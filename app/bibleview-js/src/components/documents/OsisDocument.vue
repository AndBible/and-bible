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
  <div>
    <OsisFragment :fragment="osisFragment" :show-transition="document.showTransition"/>
    <OpenAllLink :v11n="document.v11n"/>
    <FeaturesLink :fragment="osisFragment"/>
  </div>
</template>

<script>
import OsisFragment from "@/components/documents/OsisFragment";
import FeaturesLink from "@/components/FeaturesLink";
import OpenAllLink from "@/components/OpenAllLink";
import {useReferenceCollector} from "@/composables";
import {BookCategories} from "@/constants";
import {provide} from "@vue/runtime-core";

export default {
  name: "OsisDocument",
  components: {OsisFragment, FeaturesLink, OpenAllLink},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure,no-unused-vars
    const {osisFragment, bookCategory} = props.document;
    const referenceCollector = useReferenceCollector();

    if(bookCategory === BookCategories.COMMENTARIES || bookCategory === BookCategories.GENERAL_BOOK) {
      provide("referenceCollector", referenceCollector);
    }

    return {osisFragment};
  }
}
</script>
