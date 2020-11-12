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
  <div>
    <OsisFragment
        v-for="(osisFragment, index) in osisFragments" :key="index"
        :content="osisFragment"
    />
  </div>
</template>
<script>
  //import "@/code"
  import OsisFragment from "@/components/OsisFragment";
  import {provide, reactive} from "@vue/composition-api";
  import {testData} from "@/testdata";

  function useConfig() {
    return reactive({
      chapterNumbers: true,
      verseNumbers: true,
      showStrongs: false,
      showMorph: false,
      showRedLetters: false,
      versePerLine: false,
      showNonCanonical: true,
      makeNonCanonicalItalic: true,
      showTitles: true,
    })
  }

  function useStrings() {
    return {
      chapterNum: "Chapter %d. ",
      verseNum: "%d "
    }
  }

  export default {
    name: "BibleView",
    components: {OsisFragment},
    filters: {
      toId(verse) {
        return `${verse.chapter}.${verse.verse}`;
      }
    },
    setup() {
      const config = useConfig();
      const strings = useStrings();
      provide("config", config);
      provide("strings", strings);
      return {config, strings};
    },
    data() {
      return {
        osisFragments: testData,
        bookmarks: [
          {
            range: [1, 2], // ordinal range
            labels: [1, 2]
          }
        ],
        labelsStyles: [
          {
            id: 1,
            color: "#FF0000"
          },
          {
            id: 2,
            color: "#00FF00"
          }
        ]

      }
    }
  }
</script>
