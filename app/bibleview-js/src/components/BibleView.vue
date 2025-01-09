<!--
  - Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
      @click="ambiguousSelection?.handle"
      :class="{night: appSettings.nightMode, noAnimation: appSettings.disableAnimations, monochrome: appSettings.monochromeMode}"
      :style="topStyle"
      :dir="direction"
  >
    <div class="background" :style="backgroundStyle"/>
    <div :style="`height:${calculatedConfig.topOffset}px`"/>
    <div :style="modalStyle" id="modals"/>
    <template v-if="mounted">
      <BookmarkModal/>
      <AmbiguousSelection ref="ambiguousSelection"/>
    </template>
    <ErrorBox v-if="appSettings.errorBox"/>
    <div class="window-id" v-if="appSettings.errorBox">{{appSettings.windowId}}</div>
    <DevelopmentMode :current-verse="currentVerse" v-if="config.developmentMode"/>
    <div v-if="calculatedConfig.topMargin > 0" class="top-margin" :style="`height: ${calculatedConfig.topOffset}px;`"/>
    <div v-if="appSettings.hasActiveIndicator">
      <div class="top-left-corner"/>
      <div class="top-right-corner"/>
      <div class="bottom-left-corner"/>
      <div class="bottom-right-corner"/>
    </div>
    <div id="top"/>
    <div class="loading" v-if="isLoading">
      <div v-if="appSettings.disableAnimations" class="loading-icon">
        <FontAwesomeIcon size="2x" icon="fa-regular fa-clock"/>
      </div>
      <div v-else class="lds-ring"><div/><div/><div/><div/></div>
    </div>
    <div id="content" ref="topElement" :style="contentStyle">
      <div style="position: absolute; top: -5000px;" v-if="documents.length === 0">Invisible element to make fonts load properly</div>
      <DocumentBroker v-for="document in documents" :key="document.id" :document="document"/>
    </div>
    <template v-if="!modalOpen">
      <div class="prev-page-button" @click.stop="scrollUpDown(true)" :style="{width: `${calculatedConfig.marginLeft}px`}"/>
      <div class="next-page-button" @click.stop="scrollUpDown()" :style="{width: `${calculatedConfig.marginRight}px`}" />
    </template>
    <div
        v-if="appSettings.isBottomWindow"
        @touchmove.stop.prevent
        class="bottom-touch-block"
    />
    <div id="bottom"/>
  </div>
</template>
<script lang="ts" setup>
import DocumentBroker from "@/components/documents/DocumentBroker.vue";
import {computed, nextTick, onMounted, onUnmounted, provide, reactive, ref, Ref, shallowRef, watch} from "vue";
import {testBookmarkLabels, testData} from "@/testdata";
import {useInfiniteScroll} from "@/composables/infinite-scroll";
import {useGlobalBookmarks} from "@/composables/bookmarks";
import {setupEventBusListener} from "@/eventbus";
import {useScroll} from "@/composables/scroll";
import {clearLog, useAndroid} from "@/composables/android";
import {Deferred, setupWindowEventListener, waitNextAnimationFrame} from "@/utils";
import ErrorBox from "@/components/ErrorBox.vue";
import BookmarkModal from "@/components/modals/BookmarkModal.vue";
import DevelopmentMode from "@/components/DevelopmentMode.vue";
import Color from "color";
import {useStrings} from "@/composables/strings";
import {
    androidKey,
    appSettingsKey,
    calculatedConfigKey,
    configKey,
    customCssKey,
    customFeaturesKey,
    footnoteCountKey,
    globalBookmarksKey, keyboardKey,
    modalKey,
    scrollKey,
    stringsKey,
    ordinalHighlightKey
} from "@/types/constants";
import {useKeyboard} from "@/composables/keyboard";
import {useVerseNotifier} from "@/composables/verse-notifier";
import {useAddonFonts} from "@/composables/addon-fonts";
import {useFontAwesome} from "@/composables/fontawesome";
import {black, useConfig, white} from "@/composables/config";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {useModal} from "@/composables/modal";
import {useCustomCss} from "@/composables/custom-css";
import {useCustomFeatures} from "@/composables/features";
import {useSharing} from "@/composables/sharing";
import {AnyDocument, BibleViewDocumentType} from "@/types/documents";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

console.log("BibleView setup");
useAddonFonts();
useFontAwesome();
const documents: AnyDocument[] = reactive([]);
const documentType = computed<BibleViewDocumentType>(() => {
    if (documents.length < 1) {
        return "none";
    }
    return documents[0].type;
});
const {config, appSettings, calculatedConfig} = useConfig(documentType);

const lineHeight = computed(() => {
    // Update also when font settings etc are changed
    config.fontSize; config.fontFamily; config.lineSpacing;
    if (!mounted.value || !topElement.value) return 1;
    return parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height'));
});

const strings = useStrings();
window.bibleViewDebug.documents = documents;
const topElement = shallowRef<HTMLElement | null>(null);
const documentPromise: Ref<Promise<void> | null> = ref(null);
const verseHighlight = useOrdinalHighlight();
provide(ordinalHighlightKey, verseHighlight);
const {resetHighlights} = verseHighlight;

const customCss = useCustomCss();
provide(customCssKey, customCss);

const scroll = useScroll(config, appSettings, calculatedConfig, verseHighlight, documentPromise);
const {doScrolling, scrollToId} = scroll;
provide(scrollKey, scroll);
const globalBookmarks = useGlobalBookmarks(config);
const android = useAndroid(globalBookmarks, config);
const modal = useModal(android);
provide(modalKey, modal);
const keyboard = useKeyboard(android, scroll, lineHeight);
provide(keyboardKey, keyboard);

let footNoteCount = 0;

function getFootNoteCount() {
    return footNoteCount++;
}

provide(footnoteCountKey, {getFootNoteCount});

const {closeModals, modalOpen} = modal;

const mounted = ref(false);

onMounted(() => {
    mounted.value = true;
    console.log("BibleView mounted");
})
onUnmounted(() => mounted.value = false)

const {currentVerse} = useVerseNotifier(config, calculatedConfig, mounted, android, topElement, scroll, lineHeight);

const customFeatures = useCustomFeatures(android);
provide(customFeaturesKey, customFeatures);

const {documentsCleared} = useInfiniteScroll(android, documents);
const loadingCount = ref(0);

function addDocuments(...docs: AnyDocument[]) {
    async function doAddDocuments() {
        console.log("doAddDocuments, start")
        loadingCount.value++;
        await document.fonts.ready;
        await nextTick();
        // 2 animation frames seem to make sure that loading indicator is visible.
        await waitNextAnimationFrame();
        await waitNextAnimationFrame();
        documents.push(...docs);
        await nextTick();
        await Promise.all(customCss.customCssPromises);
        await waitNextAnimationFrame();
        loadingCount.value--;
        if(loadingCount.value < 0) {
            loadingCount.value = 0;
        }
        console.log(`doAddDocuments, finish, loadingCount: ${loadingCount.value}`)
    }

    documentPromise.value = doAddDocuments()
}

setupEventBusListener("config_changed", async (deferred: Deferred) => {
    const verseBeforeConfigChange = currentVerse.value;
    await deferred.wait();
    scrollToId(`o-${verseBeforeConfigChange}`, {now: true})
})

setupEventBusListener("clear_document", function clearDocument() {
    loadingCount.value = 0;
    footNoteCount = 0;
    documentsCleared();
    resetHighlights();
    closeModals();
    clearLog();
    globalBookmarks.clearBookmarks();
    documents.splice(0)
    scroll.scrollToId("top", {now: true});
});

setupEventBusListener("add_documents", addDocuments);
setupWindowEventListener("error", (e) => {
    console.error("Error caught", e.message, `on ${e.filename}:${e.colno}`);
});

if (config.developmentMode) {
    console.log("populating test data");
    globalBookmarks.updateBookmarkLabels(testBookmarkLabels)
    addDocuments(...testData)
}

let titlePrefix = ""
setupEventBusListener("set_title", function setTitle(title: string) {
    titlePrefix = title;
});

watch(documents, () => {
    if (documents.length > 0) {
        const id = documents[0].id;
        const type = documents[0].type;
        document.title = `${titlePrefix}/${type}/${id} (${process.env.NODE_ENV})`
    }
})

provide(globalBookmarksKey, globalBookmarks);
provide(configKey, config);
provide(appSettingsKey, appSettings);
provide(calculatedConfigKey, calculatedConfig);

provide(stringsKey, strings);
provide(androidKey, android);

const ambiguousSelection = ref<InstanceType<typeof AmbiguousSelection> | null>(null);

const backgroundStyle = computed(() => {
    const nightColor = appSettings.monochromeMode ? black : config.colors.nightBackground;
    const dayColor = appSettings.monochromeMode? white : config.colors.dayBackground;
    const colorInt = appSettings.nightMode ? nightColor : dayColor;
    if (colorInt === null) return "";
    const backgroundColor = Color(colorInt).hsl().string();
    return `
            background-color: ${backgroundColor};
        `;
});

const contentStyle = computed(() => {
    const nightColor = appSettings.monochromeMode? white: config.colors.nightTextColor;
    const dayColor = appSettings.monochromeMode ? black: config.colors.dayTextColor;
    const textColor = Color(appSettings.nightMode ? nightColor : dayColor);

    let style = `
          max-width: ${config.marginSize.maxWidth}mm;
          margin-left: auto;
          margin-right: auto;
          color: ${textColor.hsl().string()};
          hyphens: ${config.hyphenation ? "auto" : "none"};
          line-spacing: ${config.lineSpacing / 10}em;
          line-height: ${config.lineSpacing / 10}em;
          text-align: ${config.justifyText ? "justify" : "start"};
          font-family: ${config.fontFamily};
          font-size: ${config.fontSize*appSettings.fontSizeMultiplier}px;
          --font-size: ${config.fontSize*appSettings.fontSizeMultiplier}px;
          `;
    if (config.marginSize.marginLeft || config.marginSize.marginRight) {
        style += `
            padding-left: ${config.marginSize.marginLeft}mm;
            padding-right: ${config.marginSize.marginRight}mm;
          `;
    }
    return style;
});

const modalStyle = computed(() => {
    return `
          --bottom-offset: ${appSettings.bottomOffset}px;
          --top-offset: ${appSettings.topOffset}px;
          --font-size:${config.fontSize*appSettings.fontSizeMultiplier}px;
          --font-family:${config.fontFamily};`
});

const topStyle = computed(() => {
    const nightTextColor = appSettings.monochromeMode? white: config.colors.nightTextColor;
    const dayTextColor = appSettings.monochromeMode ? black: config.colors.dayTextColor;

    const nightBackgroundColor = appSettings.monochromeMode ? black : config.colors.nightBackground;
    const dayBackgroundColor = appSettings.monochromeMode? white : config.colors.dayBackground;

    const backgroundColor = Color(appSettings.nightMode ? nightBackgroundColor : dayBackgroundColor);
    const noiseOpacity = appSettings.nightMode ? config.colors.nightNoise : config.colors.dayNoise;
    const textColor = Color(appSettings.nightMode ? nightTextColor : dayTextColor);
    let verseNumberColor: string;
    if (appSettings.monochromeMode) {
        verseNumberColor = textColor.hsl().string();
    } else {
        verseNumberColor = appSettings.nightMode ?
            textColor.fade(0.2).hsl().string() :
            textColor.fade(0.5).hsl().string();
    }

    return `
          --bottom-offset: ${appSettings.bottomOffset}px;
          --top-offset: ${appSettings.topOffset}px;
          --noise-opacity: ${noiseOpacity / 100};
          --text-max-width: ${config.marginSize.maxWidth}mm;
          --text-color: ${textColor.hsl().string()};
          --text-color-h: ${textColor.hsl().array()[0]};
          --text-color-s: ${textColor.hsl().array()[1]}%;
          --text-color-l: ${textColor.hsl().array()[2]}%;
          --verse-number-color: ${verseNumberColor};
          --background-color: ${backgroundColor.hsl().string()};
          `;
});

setupEventBusListener("adjust_loading_count", (a: number) => {
    loadingCount.value += a;
    if (loadingCount.value < 0) {
        console.error("Loading count now below zero, setting to 0", loadingCount.value);
        loadingCount.value = 0;
    }
});

const isLoading = computed(() => documents.length === 0 || loadingCount.value > 0);

function scrollUpDown(up = false) {
    let amount =
        window.innerHeight
        - calculatedConfig.value.topOffset
        - appSettings.bottomOffset;
    if (documentType.value !== "bible" || (documentType.value === "bible" && !config.topMargin)) {
        amount -= 1.5*lineHeight.value; // 1.5 times because last line might be otherwise displayed partially
    }
    doScrolling(window.scrollY + (up ? -amount : amount), 0)
}

setupEventBusListener("scroll_down", () => scrollUpDown());
setupEventBusListener("scroll_up", () => scrollUpDown(true));

useSharing({topElement, android});
const direction = computed(() => appSettings.rightToLeft ? "rtl" : "ltr");

</script>
<style lang="scss">
@import "~@/common.scss";

$ring-size: 35px;
$ring-thickness: calc(#{$ring-size} / 12);

.loading {
  position: fixed;
  left: calc(50% - #{$ring-size} / 2);
  top: calc(50% - #{$ring-size} / 2);
}

.loading-icon {
  border-radius: 50%;
  background: white;
  .night & {
    background: black;
  }
}

$ring-color: $button-grey;

.lds-ring {
  display: inline-block;
  position: relative;
  width: $ring-size;
  height: $ring-size;

  & div {
    box-sizing: border-box;
    display: block;
    position: absolute;
    width: $ring-size;
    height: $ring-size;
    margin: 8px;
    border: $ring-thickness solid $ring-color;
    border-radius: 50%;
    animation: lds-ring 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
    border-color: $ring-color transparent transparent transparent;

    &:nth-child(1) {
      animation-delay: -0.45s;
    }

    &:nth-child(2) {
      animation-delay: -0.3s;
    }

    &:nth-child(3) {
      animation-delay: -0.15s;
    }
  }
}

@keyframes lds-ring {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.background {
  z-index: -3;
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  opacity: var(--noise-opacity);
  background-image: url("~@/assets/noise.svg");
}

$dayAlpha: 0.07;
$nightAlpha: 0.3;
$borderDistance: 0;

.active-window-corner {
  position: fixed;
  z-index: -1;
  height: 20px;
  width: 20px;
  border-width: 2.5px;

  .night & {
    border-color: rgba(196, 196, 255, 0.8);
  }

  border-color: rgba(0, 0, 255, 0.6);
}

.top-left-corner {
  @extend .active-window-corner;
  top: $borderDistance;
  left: $borderDistance;
  border-top-style: solid;
  border-left-style: solid;
}

.top-right-corner {
  @extend .active-window-corner;
  top: $borderDistance;
  right: $borderDistance;
  border-top-style: solid;
  border-right-style: solid;
}

.bottom-right-corner {
  @extend .active-window-corner;
  bottom: $borderDistance;
  right: $borderDistance;
  border-bottom-style: solid;
  border-right-style: solid;
}

.bottom-left-corner {
  @extend .active-window-corner;
  bottom: $borderDistance;
  left: $borderDistance;
  border-bottom-style: solid;
  border-left-style: solid;
}

.active-window-indicator {
  position: fixed;
  z-index: -1;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-style: solid;
  border-width: 15px;

  .night & {
    border-color: rgba(255, 255, 255, $nightAlpha);
  }

  border-color: rgba(0, 0, 0, $dayAlpha);
}

.top-margin {
  position: fixed;
  z-index: -2;
  top: 0;
  left: 0;
  right: 0;

 .night & {
    background-color: rgba(255, 255, 255, 0.15);
  }

  background-color: rgba(0, 0, 0, 0.15);

  .noAnimation & {
    background-color: unset;
    border-bottom: 1px dashed rgba(0, 0, 0, 0.5);
    font-smooth: never;
  }
  .night.noAnimation & {
    border-bottom: 1px dashed rgba(255, 255, 255, 0.5);
  }
}

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
  cursor: pointer;
}

.divider {
  height: 1em;
}

#bottom {
  padding-bottom: 200vh;
}

.modal-action-button {
  font-size: 120%;
  line-height: 0.5em; // make sure this does not increase modal title height
  &.toggled {
    color: #d5d5d5;
  }

  &.right {
    align-self: flex-end;
  }
  cursor: pointer;
  background-color: inherit;
  border: none;
  color: white;
  border-radius: 5pt;
  padding: 5pt 5pt;
  margin: 2pt 2pt;
  text-align: center;
  text-decoration: none;
  display: inline-block;
}

.window-id {
  top: var(--top-offset);
  position: fixed;
  padding: 0.5em;
  color: red;

  [dir=ltr] & {
    right: 0;
  }

  [dir=rtl] & {
    left: 0;
  }

  width: 5em;
  height: 1em;
}

.next-page-button {
  position: fixed;
  right: 0;
  bottom: 0;
  top: 0;
  width: 0;
}

.prev-page-button {
  @extend .next-page-button;
  left: 0;
  right: unset;
}

.bottom-touch-block {
  position: fixed;
  bottom: 0;
  height: 1cm;
  width: 100%;
  background: transparent;
  z-index: 10;
}

</style>
