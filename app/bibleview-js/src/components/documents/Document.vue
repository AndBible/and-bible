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
  <OsisDocument v-if="document.type === DocumentTypes.OSIS_DOCUMENT" :document="document"/>
  <BibleDocument v-else-if="document.type === DocumentTypes.BIBLE_DOCUMENT" :document="document"/>
  <ErrorDocument v-else-if="document.type === DocumentTypes.ERROR_DOCUMENT" :document="document"/>
  <MyNotesDocument v-else-if="document.type === DocumentTypes.MY_NOTES" :document="document"/>
  <StudyPadDocument v-else-if="document.type === DocumentTypes.JOURNAL" :document="document"/>
  <MultiDocument v-else-if="document.type === DocumentTypes.MULTI" :document="document"/>
</template>

<script>
import ErrorDocument from "@/components/documents/ErrorDocument";
import OsisDocument from "@/components/documents/OsisDocument";
import {DocumentTypes} from "@/constants";
import BibleDocument from "@/components/documents/BibleDocument";
import MyNotesDocument from "@/components/documents/MyNotesDocument";
import StudyPadDocument from "@/components/documents/StudyPadDocument";
import MultiDocument from "@/components/documents/MultiDocument";
export default {
  name: "Document",
  components: {MultiDocument, MyNotesDocument, BibleDocument, ErrorDocument, OsisDocument, StudyPadDocument},
  props: {
    document: {type: Object, required: true},
  },
  setup() {
    return {DocumentTypes}
  },
}
</script>

