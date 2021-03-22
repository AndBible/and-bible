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
  <div @click="ambiguousSelection.handle" :class="{night: config.nightMode}" :style="`--bottom-offset: ${config.bottomOffset}px; --top-offset: ${config.topOffset}px;`">
    <div :style="`height:${config.topOffset}px`"/>
    <div id="modals"/>
    <template v-if="mounted">
      <BookmarkModal/>
      <AmbiguousSelection ref="ambiguousSelection" @back-clicked="emit(Events.CLOSE_MODALS)"/>
    </template>
    <ErrorBox v-if="config.errorBox"/>
    <DevelopmentMode :current-verse="currentVerse" v-if="config.developmentMode"/>
    <div id="top"/>
    <div id="content" ref="topElement" :style="styleConfig">
      <Document v-for="document in documents" :key="document.id" :document="document"/>
    </div>
    <div id="bottom"/>
  </div>
</template>
<script>
  import Document from "@/components/documents/Document";
  import {nextTick, onMounted, onUnmounted, provide, reactive, watch} from "@vue/runtime-core";
  import {useAddonFonts, useConfig, useCustomCss, useFontAwesome, useVerseMap, useVerseNotifier} from "@/composables";
  import {testBookmarkLabels, testData} from "@/testdata";
  import {computed, ref} from "@vue/reactivity";
  import {useInfiniteScroll} from "@/composables/infinite-scroll";
  import {useGlobalBookmarks} from "@/composables/bookmarks";
  import {emit, Events, setupEventBusListener} from "@/eventbus";
  import {useScroll} from "@/composables/scroll";
  import {clearLog, useAndroid} from "@/composables/android";
  import {setupWindowEventListener} from "@/utils";
  import ErrorBox from "@/components/ErrorBox";
  import BookmarkModal from "@/components/modals/BookmarkModal";
  import DevelopmentMode from "@/components/DevelopmentMode";
  import Color from "color";
  import AmbiguousSelection from "@/components/modals/AmbiguousSelection";
  import {useStrings} from "@/composables/strings";

  export default {
    name: "BibleView",
    components: {Document, ErrorBox, BookmarkModal, DevelopmentMode, AmbiguousSelection},
    setup() {
      useAddonFonts();
      useFontAwesome();
      const {config} = useConfig();
      const strings = useStrings();
      const documents = reactive([]);
      window.bibleViewDebug.documents = documents;
      const topElement = ref(null);
      const verseMap = useVerseMap();
      provide("verseMap", verseMap);
      const scroll = useScroll(config, verseMap);
      const {scrollToId} = scroll;
      provide("scroll", scroll);
      const globalBookmarks = useGlobalBookmarks(config);
      const android = useAndroid(globalBookmarks, config);
      const {currentVerse} = useVerseNotifier(config, android, topElement, scroll);
      const customCss = useCustomCss();
      provide("customCss", customCss);

      useInfiniteScroll(config, android, documents);

      // TODO: rename
      async function replaceDocument(...docs) {
        await nextTick()
        documents.push(...docs)
      }

      setupEventBusListener(Events.CONFIG_CHANGED, async (deferred) => {
        const verseBeforeConfigChange = currentVerse.value;
        await deferred.wait();
        scrollToId(`v-${verseBeforeConfigChange}`, {now: true})
      })

      setupEventBusListener(Events.CLEAR_DOCUMENT, function clearDocument() {
        emit(Events.CLOSE_MODALS);
        clearLog();
        globalBookmarks.clearBookmarks();
        documents.splice(0)
      });

      setupEventBusListener(Events.REPLACE_DOCUMENT, replaceDocument);
      setupWindowEventListener("error", (e) => {
        console.error("Error caught", e.message, `on ${e.filename}:${e.colno}`);
      });

      if(config.developmentMode) {
        console.log("populating test data");
        globalBookmarks.updateBookmarkLabels(...testBookmarkLabels)
        replaceDocument(...testData)
      }

      let titlePrefix = ""
      setupEventBusListener(Events.SET_TITLE, function setTitle(title) {
        titlePrefix = title;
      });

      watch(documents, () => {
        if(documents.length > 0) {
          const id = documents[0].id;
          const type = documents[0].type;
          document.title = `${titlePrefix}/${type}/${id} (${process.env.NODE_ENV})`
        }
      })

      provide("globalBookmarks", globalBookmarks);
      provide("config", config);
      provide("strings", strings);
      provide("android", android);

      const ambiguousSelection = ref(null);

      const mounted = ref(false);
      onMounted(() => mounted.value = true)
      onUnmounted(() => mounted.value = false)

      const styleConfig = computed(() => {
          const textColor = Color(config.nightMode ? config.colors.nightTextColor: config.colors.dayTextColor);
          const backgroundColor = Color(config.nightMode ? config.colors.nightBackground: config.colors.dayBackground);

          let style = `
          max-width: ${config.marginSize.maxWidth}mm;
          margin-left: auto;
          margin-right: auto;
          color: ${textColor.hsl().string()};
          hyphens: ${config.hyphenation ? "auto": "none"};
          noise-opacity: ${config.noiseOpacity/100};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          font-family: ${config.fontFamily};
          background-color: ${backgroundColor.hsl().string()};
          font-size: ${config.fontSize}px;
          --font-size: ${config.fontSize}px;
          --background-color: ${backgroundColor.hsl().string()};
          `;
          if(config.marginSize.marginLeft || config.marginSize.marginRight) {
            style += `
            margin-left: ${config.marginSize.marginLeft}mm;
            margin-right: ${config.marginSize.marginRight}mm;
          `;
          }
          return style;
      });

      return {
        makeBookmarkFromSelection: globalBookmarks.makeBookmarkFromSelection,
        updateBookmarks: globalBookmarks.updateBookmarks, ambiguousSelection,
        config, strings, documents, topElement, currentVerse, mounted, emit, Events, styleConfig,
      };
    },
  }
</script>
<style lang="scss">
@import "~@/common.scss";
a {
  color: blue;
  .night & {
    color: #7b7bff;
  }
}

.bookmark-marker {
  @extend .superscript;
  font-size: 50%;
  top: -0.8em;
}

.divider {
  height: 1em;
}

#bottom {
  padding-bottom: 100vh;
}

.button {
  font-size: 90%;
  background-color: #717171;
  + .toggled {
    background-color: #474747;
    &.light {
      background-color: #d5d5d5;
    }
  }
  &.light {
    background-color: #bdbdbd;
    color: black;
  }
  &.right {
    align-self: end;
  }
  border: none;
  color: white;
  padding: 5pt 5pt;
  border-radius: 5pt;
  margin: 2pt 2pt;
  text-align: center;
  text-decoration: none;
  display: inline-block;
}
</style>
