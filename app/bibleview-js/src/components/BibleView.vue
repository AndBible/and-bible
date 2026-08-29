<!--
  - Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
      :class="{night: appSettings.nightMode, noAnimation: appSettings.disableAnimations, monochrome: appSettings.monochromeMode, colorEink: appSettings.colorEinkMode}"
      :style="topStyle"
      :dir="direction"
  >
    <div class="background"/>
    <div v-if="backgroundImageStyle" class="background-image" :style="backgroundImageStyle"/>
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
    <ChapterNavigationButtons
      v-if="showChapterNavButtons"
      position="top"
      :loading="loadingAtTop"
      @load-more="loadTextAtTop"
      @navigate-prev="android.goToPreviousChapter"
      @navigate-next="android.goToNextChapter"
    />
    <div class="loading" v-if="isLoading">
      <LoadingSpinner/>
    </div>
    <div id="content" ref="topElement" :style="contentStyle">
      <div style="position: absolute; top: -5000px;" v-if="documents.length === 0">Invisible element to make fonts load properly</div>
      <DocumentBroker v-for="document in documents" :key="document.id" :document="document"/>
      <div class="infinite-scroll-loading" v-if="loadingAtEnd">
        <LoadingSpinner small/>
      </div>
    </div>
    <template v-if="!modalOpen">
      <div class="prev-page-button" @click.stop="scrollUpDown(true)" :style="{width: `${calculatedConfig.marginLeft}px`}"/>
      <div class="next-page-button" @click.stop="scrollUpDown()" :style="{width: `${calculatedConfig.marginRight}px`}" />
    </template>
    <div class="pagenumber"
         :style="{bottom: pageNumberBottom}"
         v-if="config.showPageNumber"
    >
      <div class="pagenumber-text">
        {{ pageNumber }}/{{ pageCount }}
      </div>
    </div>
    <ReadingProgress v-if="config.showReadingProgress" :text="progressText" :bottom="readingProgressBottom"/>
    <template v-if="appSettings.einkMode && config.scrollHelperLines && config.pageScrollAmount < 100">
      <div
          v-for="pos in helperLinePositions"
          :key="pos"
          class="scroll-helper-line"
          :class="helperLineClass"
          :style="{top: `${pos}px`}"
      />
    </template>
    <div v-if="appSettings.einkMode && config.showPageButtons" class="page-buttons" :style="{bottom: `${(appSettings.isBottomWindow ? appSettings.bottomOffset : 0) + 12}px`}">
      <button class="page-button" @click.stop="scrollUpDown(true)">
        <FontAwesomeIcon :icon="faChevronUp"/>
      </button>
      <button class="page-button" @click.stop="scrollUpDown()">
        <FontAwesomeIcon :icon="faChevronDown"/>
      </button>
    </div>
    <div
        v-if="appSettings.isBottomWindow"
        @touchmove.stop.prevent
        :style="{height: `${appSettings.bottomOffset}px`}"
        class="bottom-touch-block"
    />
    <div
        v-if="appSettings.isBottomWindow && !appSettings.bottomOffset"
        @touchmove.stop.prevent
        class="invisible-bottom-touch-block"
    />
    <ChapterNavigationButtons
      v-if="showChapterNavButtons"
      position="bottom"
      :loading="loadingAtEnd"
      :reached-end="reachedEnd"
      @load-more="loadTextAtEnd"
      @navigate-prev="android.goToPreviousChapter"
      @navigate-next="android.goToNextChapter"
    />
    <div id="bottom" ref="bottomElement"/>
  </div>
</template>
<script lang="ts" setup>
import DocumentBroker from "@/components/documents/DocumentBroker.vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faChevronUp, faChevronDown} from "@fortawesome/free-solid-svg-icons";
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
    globalBookmarksKey,
    keyboardKey,
    memorizationKey,
    modalKey,
    scrollKey,
    stringsKey,
    ordinalHighlightKey
} from "@/types/constants";
import {useKeyboard} from "@/composables/keyboard";
import {useMemorization} from "@/composables/memorization";
import {useVerseNotifier} from "@/composables/verse-notifier";
import {useAddonFonts} from "@/composables/addon-fonts";
import {useFontAwesome} from "@/composables/fontawesome";
import {black, useConfig, white} from "@/composables/config";
import {calcHelperLinePositions, calcMaxScrollY, calcPageScrollDistance, calcRelativePageNumbers} from "@/composables/page-scroll";
import {useOrdinalHighlight} from "@/composables/ordinal-highlight";
import {useModal} from "@/composables/modal";
import {useCustomCss} from "@/composables/custom-css";
import {useCustomFeatures} from "@/composables/features";
import {useSharing} from "@/composables/sharing";
import {AnyDocument, BibleViewDocumentType} from "@/types/documents";
import AmbiguousSelection from "@/components/modals/AmbiguousSelection.vue";
import ChapterNavigationButtons from "@/components/ChapterNavigationButtons.vue";
import LoadingSpinner from "@/components/LoadingSpinner.vue";
import ReadingProgress from "@/components/ReadingProgress.vue";
import {ProgressDoc, useReadingProgress} from "@/composables/use-reading-progress";
import {backgroundImageLayer} from "@/code/background-image";

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
const bottomElement = shallowRef<HTMLElement | null>(null);
const documentPromise: Ref<Promise<void> | null> = ref(null);
const verseHighlight = useOrdinalHighlight();
provide(ordinalHighlightKey, verseHighlight);
const {resetHighlights} = verseHighlight;

const customCss = useCustomCss();
provide(customCssKey, customCss);

const scroll = useScroll(config, appSettings, calculatedConfig, verseHighlight, documentPromise);
const {doScrolling, scrollToId, scrollY} = scroll;
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

// End of the text and viewport height, measured for the page-number overlay.
// #bottom sits right after the text and carries a tall padding so the reader can
// scroll past the last line, so its offsetTop — not scrollHeight — is where the
// content actually ends. Kept fresh by a ResizeObserver so the numbers also
// follow infinite-scroll loading, font/margin changes and rotation.
const contentEnd = ref(0);
const viewportHeight = ref(0);
let contentResizeObserver: ResizeObserver | null = null;

function updateContentMetrics() {
    contentEnd.value = bottomElement.value?.offsetTop ?? document.documentElement.scrollHeight;
    viewportHeight.value = window.innerHeight;
}

const maxScrollY = computed(() =>
    calcMaxScrollY(contentEnd.value, viewportHeight.value, appSettings.bottomOffset)
);

onMounted(() => {
    mounted.value = true;
    updateContentMetrics();
    contentResizeObserver = new ResizeObserver(updateContentMetrics);
    contentResizeObserver.observe(document.documentElement);
    console.log("BibleView mounted");
})
onUnmounted(() => {
    mounted.value = false;
    contentResizeObserver?.disconnect();
    contentResizeObserver = null;
})

const {currentVerse, currentKey} = useVerseNotifier(calculatedConfig, android, scroll, lineHeight);
const {progressText} = useReadingProgress(config, documents as ProgressDoc[], currentVerse, currentKey, calculatedConfig, topElement, strings);

const customFeatures = useCustomFeatures(android);
provide(customFeaturesKey, customFeatures);

const {
    documentsCleared,
    loadingAtEnd,
    loadingAtTop,
    loadTextAtTop,
    loadTextAtEnd,
    documentSupportsChapterNavigation,
    infiniteScrollIsEnabled,
    reachedEnd
} = useInfiniteScroll(android, documents, config);

const showChapterNavButtons = computed(() => {
    return documentSupportsChapterNavigation.value && !infiniteScrollIsEnabled.value;
});
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

const memorization = useMemorization(config);
provide(memorizationKey, memorization);

const ambiguousSelection = ref<InstanceType<typeof AmbiguousSelection> | null>(null);

const backgroundImageStyle = computed(() => {
    const layer = backgroundImageLayer(config.colors, {
        nightMode: appSettings.nightMode,
        monochromeMode: appSettings.monochromeMode,
        einkMode: appSettings.einkMode,
    });
    if (layer === null) return null;
    return `background-image: url('${layer.url}'); opacity: ${layer.opacity};`;
});

const contentStyle = computed(() => {
    const nightColor = appSettings.monochromeMode? white: config.colors.nightTextColor;
    const dayColor = appSettings.monochromeMode ? black: config.colors.dayTextColor;
    const textColor = Color(appSettings.nightMode ? nightColor : dayColor);

    let style = `
          box-sizing: border-box;
          max-width: ${config.marginSize.maxWidth}mm;
          margin-left: auto;
          margin-right: auto;
          color: ${textColor.hsl().string()};
          hyphens: ${config.hyphenation ? "auto" : "none"};
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
    // Clear error documents when loading starts to avoid spinner on top of old error
    // Only clear if first document is an error - preserve normal content during reload
    if (a > 0 && documents.length > 0 && documents[0].type === "error") {
        documents.splice(0);
    }
});

setupEventBusListener("reset_loading_count", () => {
    loadingCount.value = 0;
});

const isLoading = computed(() => documents.length === 0 || loadingCount.value > 0);
const scrollAmount = computed(() =>
    calcPageScrollDistance(
        calculatedConfig.value.pageHeight,
        calculatedConfig.value.topMargin,
        config.pageScrollAmount,
        lineHeight.value,
    )
)

function scrollUpDown(up = false) {
    doScrolling(window.scrollY + (up ? -scrollAmount.value : scrollAmount.value), 0)
}

const helperLinePositions = computed(() =>
    calcHelperLinePositions(
        config.pageScrollAmount,
        calculatedConfig.value.topOffset,
        calculatedConfig.value.pageHeight,
        calculatedConfig.value.topMargin,
    )
);

const helperLineClass = computed(() => {
    switch (config.scrollHelperLineStyle) {
        case 1: return 'helper-line-thin-solid';
        case 2: return 'helper-line-thick-solid';
        default: return 'helper-line-thin-dotted';
    }
});

const pageNumberBottom = computed(() =>
    appSettings.isBottomWindow && !appSettings.bottomOffset ? '1cm' : `${appSettings.bottomOffset}px`
);

const readingProgressBottom = computed(() => {
    // Same base as the page-number overlay so we clear the window button bar / bottom offset.
    const base = appSettings.isBottomWindow && !appSettings.bottomOffset ? '1cm' : `${appSettings.bottomOffset}px`;
    // Stack above the page-number overlay (~0.7cm tall at the same base) when it is also shown.
    return config.showPageNumber ? `calc(${base} + 0.7cm)` : base;
});

const pageNumbers = computed(() =>
    calcRelativePageNumbers(scrollY.value, maxScrollY.value, scrollAmount.value)
);
const pageNumber = computed(() => pageNumbers.value.current.toFixed(1));
const pageCount = computed(() => pageNumbers.value.total);

setupEventBusListener("scroll_down", () => scrollUpDown());
setupEventBusListener("scroll_up", () => scrollUpDown(true));

useSharing({topElement, android});
const direction = computed(() => appSettings.rightToLeft ? "rtl" : "ltr");

</script>
<style lang="scss">
@use "@/common.scss" as *;

.loading {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

.infinite-scroll-loading {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.background {
  z-index: -2;
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  opacity: var(--noise-opacity);
  background-image: url("~@/assets/noise.svg");
}

.background-image {
  z-index: -3;
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  pointer-events: none;
}

$dayAlpha: 0.07;
$nightAlpha: 0.3;
$borderDistance: 0;
$colorEinkAccent: rgba(0, 0, 255, 0.6);
$colorEinkAccentNight: rgba(196, 196, 255, 0.8);

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

  .monochrome & {
    border-color: black;
  }
  .monochrome.night & {
    border-color: white;
  }
  .colorEink & {
    border-color: $colorEinkAccent;
  }
  .colorEink.night & {
    border-color: $colorEinkAccentNight;
  }
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

  .monochrome & {
    border-color: black;
  }
  .monochrome.night & {
    border-color: white;
  }
  .colorEink & {
    border-color: $colorEinkAccent;
  }
  .colorEink.night & {
    border-color: $colorEinkAccentNight;
  }
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
  // Accent blue at lower alpha (cannot reuse $colorEinkAccent — different alpha)
  .colorEink & {
    background-color: rgba(0, 0, 255, 0.25);
  }
  .colorEink.night & {
    background-color: rgba(196, 196, 255, 0.35);
  }
  .colorEink.noAnimation & {
    background-color: unset;
    border-bottom: 1px dashed $colorEinkAccent;
  }
  .colorEink.night.noAnimation & {
    border-bottom: 1px dashed $colorEinkAccentNight;
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
  font-size: 60%;
  top: -0.8em;
  cursor: pointer;
  > .bookmark-marker-note {
    @extend .superscript;
    font-size: 60%;
    top: -0.5em;
    padding-left: 2px;
  }
}

.ai-doc-marker {
  padding-left: 2px;
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

.pagenumber {
  z-index: 5;
  position: fixed;
  right: 2mm;
  margin-bottom: 2mm;
  bottom: 0;
  // The label holds "current/total", so it grows leftwards with the page count
  // instead of the text spilling out of a fixed-width pill.
  min-width: 1cm;
  padding: 0 1mm;
  height: 0.5cm;
  font-size: 70%;
  font-weight: bold;
  color: var(--text-color);
  background: rgba(207, 207, 207, 0.71);
  .noAnimation & {
    background-color: var(--background-color);
    border-width: 1px;
    border-style: solid;
    border-color: var(--text-color);
  }
  border-radius: 0.5cm;
  display: flex;
  align-items: center;
  justify-content: center;
  .pagenumber-text {
    white-space: nowrap;
  }
}

.prev-page-button {
  @extend .next-page-button;
  left: 0;
  right: unset;
}

.invisible-bottom-touch-block {
  position: fixed;
  bottom: 0;
  height: 1cm;
  width: 100%;
  background: transparent;
  z-index: 10;
}

.bottom-touch-block {
  position: fixed;
  bottom: 0;
  width: 100%;
  background: var(--background-color);

  .noAnimation & {
    background: var(--background-color);
    border-color: var(--text-color);
    border-top-style: dashed;
    border-width: 1px;
  }
  z-index: 10;
}

.scroll-helper-line {
  position: fixed;
  left: 0;
  right: 0;
  height: 0;
  z-index: 4;
  pointer-events: none;

  &.helper-line-thin-dotted {
    border-top: 1px dotted var(--text-color);
    opacity: 0.3;
  }

  &.helper-line-thin-solid {
    border-top: 1px solid var(--text-color);
    opacity: 0.3;
  }

  &.helper-line-thick-solid {
    border-top: 2px solid var(--text-color);
    opacity: 0.3;
  }

  .colorEink & {
    border-top-color: $colorEinkAccent;
    opacity: 1;
  }
  .colorEink.night & {
    border-top-color: $colorEinkAccentNight;
  }
}

.page-buttons {
  position: fixed;
  z-index: 6;
  display: flex;
  flex-direction: column;
  gap: 4px;

  [dir=ltr] & {
    left: 8px;
  }

  [dir=rtl] & {
    right: 8px;
  }

  .page-button {
    width: 54px;
    height: 54px;
    border-radius: 50%;
    border: 1px solid var(--text-color);
    background: var(--background-color);
    color: var(--text-color);
    opacity: 0.6;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 21px;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;

    &:active {
      opacity: 0.9;
    }

    .monochrome & {
      background: white;
      border-color: black;
      color: black;
      opacity: 1;
    }
  }
}

</style>
