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
  <OsisDocument v-if="document.type === DocumentTypes.OSIS_DOCUMENT" :document="document"/>
  <BibleDocument v-else-if="document.type === DocumentTypes.BIBLE_DOCUMENT" :document="document"/>
  <ErrorDocument v-else-if="document.type === DocumentTypes.ERROR_DOCUMENT" :document="document"/>
  <MyNotesDocument v-else-if="document.type === DocumentTypes.MY_NOTES" :document="document"/>
</template>

<script>
  import ErrorDocument from "@/components/documents/ErrorDocument";
  import OsisDocument from "@/components/documents/OsisDocument";
  import {DocumentTypes} from "@/constants";
  import BibleDocument from "@/components/documents/BibleDocument";
  import {provide} from "@vue/runtime-core";
  import MyNotesDocument from "@/components/documents/MyNotesDocument";
  export default {
    name: "Document",
    components: {MyNotesDocument, BibleDocument, ErrorDocument, OsisDocument},
    props: {
      document: {type: Object, required: true},
    },
    setup(props) {
      if([DocumentTypes.BIBLE_DOCUMENT, DocumentTypes.OSIS_DOCUMENT].includes(props.document.type)) {
        // eslint-disable-next-line vue/no-setup-props-destructure
        const {bookInitials, bookName, bookAbbreviation, key} = props.document;
        provide("documentInfo", {bookInitials, bookName, bookAbbreviation, key});
      }

      return {DocumentTypes}
    },
  }
</script>

