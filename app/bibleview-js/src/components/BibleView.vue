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
    <div class="highlightButton"><span @click="highLight">Highlight!</span> <span @mouseenter="getSelection">Get selection!</span></div>
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
  import highlightRange from "dom-highlight-range";

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
    },
    methods: {
      highLight() {
        //const first = document.getElementById("2Thess.2.12");
        //const second = document.getElementById("2Thess.2.15");
        const startCount = 16;
        const endCount = 53;
        const startOff = 75;
        const endOff = 78;
        const first = document.querySelector(`[data-element-count="${startCount}"]`).childNodes[0];
        const second = document.querySelector(`[data-element-count="${endCount}"]`).childNodes[0];
        const range = new Range();
        range.setStart(first, startOff);
        range.setEnd(second, endOff);
        const removeHighlights = highlightRange(range, 'span', { class: 'highlighted' });
      },
      getSelection() {

        const selection = window.getSelection();
        if (!selection.isCollapsed) {
          const range = selection.getRangeAt(0);
          console.log("range", range);

          const findElemWithOsisID = (elem) => {
            // This needs to be done unique for each OsisFragment (as there can be many).
            if(elem.dataset && elem.dataset.osisID) {
              return elem;
            }
            else {
              return findElemWithOsisID(elem.parentElement);
            }
          }

          const startElem = findElemWithOsisID(range.startContainer);
          const endElem = findElemWithOsisID(range.endContainer);

          console.log(
              startElem.dataset.osisID,
              startElem.dataset.elementCount,
              range.startOffset
          );
          console.log(
              endElem.dataset.osisID,
              endElem.dataset.elementCount,
              range.endOffset
          );
        }
      }
    },
  }
</script>
<style>
.highlighted {
  background-color: yellow;
}
.highlightButton {
  position: fixed;
  bottom: 0;
  left: 0;
  background: yellow;
}
.inlineDiv {
  display: inline;
}
</style>
