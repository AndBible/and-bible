<!--
  - Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
  -
  - This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
  -
  - AndBible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with AndBible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<template>
  <div
      class="document"
      :data-book-initials="bookInitials"
  >
    <OsisFragment :fragment="osisFragment"/>
    <OpenAllLink :v11n="document.v11n"/>
    <FeaturesLink :fragment="osisFragment"/>
  </div>
</template>

<script setup lang="ts">
import OsisFragment from "@/components/documents/OsisFragment.vue";
import FeaturesLink from "@/components/FeaturesLink.vue";
import OpenAllLink from "@/components/OpenAllLink.vue";
import {useReferenceCollector} from "@/composables";
import {referenceCollectorKey} from "@/types/constants";
import {provide} from "vue";
import {OsisDocument} from "@/types/documents";

const props = defineProps<{ document: OsisDocument }>();

// eslint-disable-next-line vue/no-setup-props-destructure,no-unused-vars
const {osisFragment, bookCategory, bookInitials} = props.document;
const referenceCollector = useReferenceCollector();

if (bookCategory === "COMMENTARY" || bookCategory === "GENERAL_BOOK") {
    provide(referenceCollectorKey, referenceCollector);
}
</script>
