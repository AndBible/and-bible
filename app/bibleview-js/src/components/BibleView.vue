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
  <div :style="`--toolbar-offset: ${config.toolbarOffset}px`">
    <div :style="`height:${config.toolbarOffset}px`"/>
    <div id="notes"/>
    <NotesModal/>
    <ErrorBox :log-entries="logEntries"/>
    <DevelopmentMode :current-verse="currentVerse" v-if="config.developmentMode"/>
    <div id="top" ref="topElement" :style="styleConfig">
      <div v-for="({contents, showTransition}, index) in osisFragments" :key="index">
        <template v-for="(data, idx) in contents" :key="data.key">
          <div :id="`f-${data.key}`" class="fragment">
            <OsisFragment :show-transition="showTransition" :data="data"/>
          </div>
          <div v-if="contents.length > 0 && idx < contents.length" class="divider" />
        </template>
      </div>
    </div>
    <div id="bottom"/>
  </div>
</template>
<script>
  import OsisFragment from "@/components/OsisFragment";
  import {provide, reactive, watch} from "@vue/runtime-core";
  import {useConfig, useFontAwesome, useStrings, useVerseNotifier} from "@/composables";
  import {testData} from "@/testdata";
  import {ref} from "@vue/reactivity";
  import {useInfiniteScroll} from "@/composables/infinite-scroll";
  import {useGlobalBookmarks} from "@/composables/bookmarks";
  import {Events, setupEventBusListener} from "@/eventbus";
  import {useScroll} from "@/composables/scroll";
  import {useAndroid} from "@/composables/android";
  import {setupWindowEventListener} from "@/utils";
  import ErrorBox from "@/components/ErrorBox";
  import NotesModal from "@/components/NotesModal";
  import DevelopmentMode from "@/components/DevelopmentMode";

  export default {
    name: "BibleView",
    components: {OsisFragment, ErrorBox, NotesModal, DevelopmentMode},
    setup() {
      useFontAwesome();

      const {config} = useConfig();
      const strings = useStrings();
      const osisFragments = reactive([]);
      const topElement = ref(null);
      const {scrollToVerse} = useScroll(config);
      const globalBookmarks = useGlobalBookmarks(config);
      const {logEntries, ...android} = useAndroid(globalBookmarks);
      const {currentVerse} = useVerseNotifier(config, android, topElement);
      useInfiniteScroll(config, android, osisFragments);

      watch(() => osisFragments, () => {
        for(const frag of osisFragments) {
            globalBookmarks.updateBookmarkLabels(...frag.bookmarkLabels);
            globalBookmarks.updateBookmarks(...frag.bookmarks);
        }
      }, {deep: true});

      function replaceOsis(...s) {
        logEntries.splice(0);
        osisFragments.splice(0)
        osisFragments.push(...s)
      }

      setupEventBusListener(Events.CONFIG_CHANGED, async (deferred) => {
        const verseBeforeConfigChange = currentVerse.value;
        await deferred.wait();
        scrollToVerse(`v-${verseBeforeConfigChange}`, true)
      })

      setupEventBusListener(Events.REPLACE_OSIS, replaceOsis);
      setupWindowEventListener("error", (e) => {
        console.error("Error caught", e.message, `on ${e.filename}:${e.colno}`);
      });

      if(process.env.NODE_ENV === "development") {
        console.log("populating test data");
        replaceOsis(...testData)
      }

      setupEventBusListener(Events.SET_TITLE,
          (title) => document.title = `${title} (${process.env.NODE_ENV})`
      );

      provide("globalBookmarks", globalBookmarks);
      provide("config", config);
      provide("strings", strings);
      provide("android", android);

      return {
        makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
        updateBookmarks: globalBookmarks.updateBookmarks,
        config, strings, osisFragments, topElement, logEntries, currentVerse
      };
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
  }
</script>
<style lang="scss">

.icon {
  width: 0.7em;
  height: 0.7em;
  font-size: 80%;
  padding: 1pt;
  vertical-align: super;
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

.button {
  background-color: #717171;
  border: none;
  color: white;
  padding: 2pt 2pt;
  margin: 2pt 2pt;
  text-align: center;
  text-decoration: none;
  display: inline-block;
}
</style>
