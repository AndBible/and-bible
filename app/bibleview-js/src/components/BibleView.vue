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
    <div :style="styleConfig">
      <OsisFragment
          v-for="(osisFragment, index) in osisFragments" :key="index"
          :content="osisFragment"
      />
    </div>
  </div>
</template>
<script>
  import OsisFragment from "@/components/OsisFragment";
  import {provide} from "@vue/runtime-core";
  import {testData} from "@/testdata";
  import highlightRange from "dom-highlight-range";
  import {useBookmarkLabels, useBookmarks, useConfig, useStrings} from "@/composables";

  export default {
    name: "BibleView",
    components: {OsisFragment},
    setup() {
      const config = useConfig();
      const strings = useStrings();
      const bookmarks = useBookmarks();
      const bookmarkLabels = useBookmarkLabels();

      provide("bookmarks", bookmarks);
      provide("bookmarkLabels", bookmarkLabels);
      provide("config", config);
      provide("strings", strings);

      return {config, strings, osisFragments: testData};
    },
    computed: {
      styleConfig({config}) {
        let style = `
          max-width: ${config.maxWidth};
          color: ${config.textColor};
          hyphens: ${config.hyphenation ? "auto": "none"};
          noise-opacity: ${config.noiseOpacity/100};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          `;
        if(config.marginLeft || config.marginRight) {
          style += `
            margin-left: ${config.marginLeft}mm;
            margin-right: ${config.marginRight}mm;
          `;
        }
        return style;
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
