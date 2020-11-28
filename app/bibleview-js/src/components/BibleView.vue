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
  <div :style="`height:${config.toolbarOffset}px`"/>
  <div id="notes"/>
  <div v-if="config.developmentMode"
      :style="`position: fixed; top:0; width:100%;  background-color: rgba(100, 255, 100, 0.7);
               height:${config.toolbarOffset}px`"
  >
     Current verse: {{currentVerse}}
  </div>
  <div v-if="config.developmentMode" class="highlightButton"><span v-show="false" @click="highLight">Highlight!</span> <span @mouseenter="getSelection">Get selection!</span></div>
  <div id="top" ref="topElement" :style="styleConfig">
    <div v-for="({contents}, index) in osisFragments" :key="index">
      <template v-for="({xml, key}, idx) in contents" :key="key">
        <OsisFragment :xml="xml" :fragment-key="`${key}`"/>
        <div v-if="contents.length > 0 && idx < contents.length" class="divider" />
      </template>
    </div>
  </div>
  <div id="bottom"/>
</template>
<script>
  import OsisFragment from "@/components/OsisFragment";
  import {onMounted, onUnmounted, provide, reactive, watch} from "@vue/runtime-core";
  import highlightRange from "dom-highlight-range";
  import {useAndroid, useBookmarks, useConfig, useStrings, useVerseNotifier} from "@/composables";
  import {testData} from "@/testdata";
  import {ref} from "@vue/reactivity";
  import {useInfiniteScroll} from "@/code/infinite-scroll";

  function findElemWithOsisID(elem) {
    if(elem === null) return;
    // This needs to be done unique for each OsisFragment (as there can be many).
    if(elem.dataset && elem.dataset.osisID) {
      return elem;
    }
    else if (elem.parentElement) {
      return elem.parentElement.closest('.osis') //findElemWithOsisID(elem.parentElement);
    }
  }
  let lblCount = 0;
  export default {
    name: "BibleView",
    components: {OsisFragment},
    setup() {
      const {config} = useConfig();
      const strings = useStrings();
      const android = useAndroid();
      const osisFragments = reactive([]);
      const topElement = ref(null);
      useInfiniteScroll(config, android, osisFragments);
      const {currentVerse} = useVerseNotifier(config, android, topElement);
      const bookmarks = useBookmarks();

      watch(() => osisFragments, () => {
        for(const frag of osisFragments) {
            bookmarks.updateBookmarks(frag.bookmarks);
            bookmarks.updateBookmarkLabels(frag.bookmarkLabels);
        }
      }, {deep: true});

      window.bibleViewDebug.osisFragments = osisFragments;

      function replaceOsis(...s) {
        osisFragments.splice(0)
        osisFragments.push(...s)
      }

      if(process.env.NODE_ENV === "development") {
        console.log("populating test data");
        replaceOsis(...testData)
      }

      window.bibleView.replaceOsis = replaceOsis;
      window.bibleView.setTitle = (title) => {
        document.title = `${title} (${process.env.NODE_ENV})`;
      }
      const handler = (ev) => {
        const {x, y} = ev;
        const element = document.elementFromPoint(x, y)
        //console.log("elem", element, element.parentElement, element.parentElement.parentElement);
        //const osisElem = element.closest(".osis");
        const osisElem = findElemWithOsisID(element);
        if(osisElem) {
          console.log(osisElem.dataset.osisID);
        }

        ev.preventDefault()
        ev.stopPropagation()
      };
      const evType = "mouseover";
      onMounted(() => {
        window.addEventListener(evType, handler)
      })

      onUnmounted( () => {
        window.removeEventListener(evType, handler)
      });

      bookmarks.updateBookmarkLabels([{
        id: -5,
        style: {color: [255,0,0,255]}
      },
        {
          id: -1,
          style: {color: [255,0,0,255]}
        },

        {
          id: -2,
          style: {color: [255,255,0,255]}
        },
        {
          id: -3,
          style: {color: [255,0,255,255]}
        },
        {
          id: -4,
          style: {color: [0,255,255,255]}
        }
      ])

      provide("bookmarks", bookmarks);
      provide("config", config);
      provide("strings", strings);
      provide("android", android);
      console.log("android", android);
      return {config, strings, osisFragments, topElement, currentVerse, updateBookmarks: bookmarks.updateBookmarks};
    },
    computed: {
      styleConfig({config}) {
        let style = `
          max-width: ${config.marginSize.maxWidth};
          color: ${config.textColor};
          hyphens: ${config.hyphenation ? "auto": "none"};
          noise-opacity: ${config.noiseOpacity/100};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          `;
        if(config.marginSize.marginLeft || config.marginSize.marginRight) {
          style += `
            margin-left: ${config.marginSize.marginLeft}mm;
            margin-right: ${config.marginSize.marginRight}mm;
          `;
        }
        return style;
      }
    },
    mounted() {
      window.bibleView.highlight1 = () => {
        console.log("Highlight 1 JS!");
        this.getSelection()
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
        const range = selection.getRangeAt(0);
        console.log("range", range);

        const startElem = findElemWithOsisID(range.startContainer);
        const endElem = findElemWithOsisID(range.endContainer);
        this.updateBookmarks([{
          id: -lblCount,
          range: [parseInt(startElem.dataset.ordinal), parseInt(endElem.dataset.ordinal)],
          element: [parseInt(startElem.dataset.elementCount), parseInt(endElem.dataset.elementCount)],
          offset: [range.startOffset, range.endOffset],
          labels: [-(lblCount++ % 5 + 1)]
        }])
        //const startElem = range.startContainer.closest(".osis");
        //const endElem = range.endContainer.closest(".osis");

        console.log(
            startElem.dataset.ordinal,
            startElem.dataset.elementCount,
            range.startOffset
        );
        console.log(
            endElem.dataset.ordinal,
            endElem.dataset.elementCount,
            range.endOffset
        );
        return range
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
  padding: 2em;
  background: yellow;
}
.inlineDiv {
  display: inline;
}

.divider {
  height: 1em;
}

#bottom {
  padding-bottom: 100vh;
}
</style>
