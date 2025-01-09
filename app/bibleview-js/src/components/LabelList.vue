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
  <BookmarkLabelActions :bookmark-id="bookmarkId" ref="actions"/>

  <div class="label-list" :class="{singleLine}">
    <div
        @touchstart="labelClicked($event, label)"
        @mousedown="labelClicked($event, label)"
        @click="labelClicked($event, label)"
        v-for="label in labels"
        :key="label.id"
        :style="labelStyle(label)"
        class="label"
        :class="{notAssigned: !isAssigned(label.id)}"
    >
      <span v-if="isPrimary(label)" class="icon"><FontAwesomeIcon icon="bookmark"/></span>
      {{ label.name }}
    </div>
    <div style="width: 30px;"/>
  </div>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import {computed, inject, ref, Ref, watch} from "vue";
import {addAll, clickWaiter, removeAll} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {sortBy} from "lodash";
import {androidKey, globalBookmarksKey, locateTopKey} from "@/types/constants";
import {BaseBookmark, LabelAndStyle} from "@/types/client-objects";
import BookmarkLabelActions from "@/components/modals/BookmarkLabelActions.vue";

const props = withDefaults(defineProps<{
    bookmarkId: IdType
    handleTouch: boolean
    disableLinks: boolean
    favourites: boolean
    frequent: boolean
    recent: boolean
    inBookmark: boolean
    onlyAssign: boolean
    singleLine: boolean
}>(), {
    handleTouch: false,
    disableLinks: false,
    favourites: false,
    frequent: false,
    recent: false,
    inBookmark: false,
    onlyAssign: false,
    singleLine: false,
});

const emit = defineEmits(["has-entries"]);

const {adjustedColor, appSettings} = useCommon();
const android = inject(androidKey)!;
const actions: Ref<InstanceType<typeof BookmarkLabelActions> | null> = ref(null);

function labelStyle(label: LabelAndStyle) {
    if (appSettings.monochromeMode) {
        return "";
    }
    const color = adjustedColor(label.color);
    if (isAssigned(label.id)) {
        const textColor = color.isLight() ? "var(--label-text-black)" : "var(--label-text-white)";
        return `background-color: ${color.string()}; color: ${textColor};`;
    } else {
        return `border-color: ${color.string()};`;
    }
}

const {bookmarkMap, bookmarkLabels} = inject(globalBookmarksKey)!;
const bookmark = computed<BaseBookmark>(() => bookmarkMap.get(props.bookmarkId)!);

function isAssigned(labelId: IdType) {
    return bookmark.value.labels.includes(labelId);
}

function isPrimary(label: LabelAndStyle) {
    return label.id === bookmark.value.primaryLabelId;
}

const labels = computed<LabelAndStyle[]>(() => {
    if (!bookmark.value) return [];
    const shown: Set<IdType> = new Set();
    const earlier = new Set();
    if (props.inBookmark) {
        addAll(shown, ...bookmark.value.labels);
    }
    addAll(earlier, ...bookmark.value.labels);
    if (props.favourites) {
        addAll(shown, ...appSettings.favouriteLabels);
        removeAll(shown, ...earlier);
    }
    addAll(earlier, ...appSettings.favouriteLabels);
    if (props.recent) {
        addAll(shown, ...appSettings.recentLabels);
        removeAll(shown, ...earlier);
    }
    addAll(earlier, ...appSettings.recentLabels);
    if (props.frequent) {
        addAll(shown, ...appSettings.frequentLabels);
        removeAll(shown, ...earlier);
    }
    //addAll(earlier, ...appSettings.frequentLabels);
    // TODO: add frequent
    return sortBy(
        Array.from(shown).map(
            (labelId: IdType) => bookmarkLabels.get(labelId)!).filter(v => v),
        ["name"]
    );
});

watch(labels, v => {
    emit("has-entries", v.length > 0);
}, {immediate: true});

const {waitForClick} = clickWaiter(props.handleTouch);

const locateTop = inject(locateTopKey, ref(true));

async function labelClicked(event: MouseEvent | TouchEvent, label: LabelAndStyle) {
    if (props.disableLinks) return;
    if (!await waitForClick(event)) return;

    if (!isAssigned(label.id)) {
        android.toggleBookmarkLabel(bookmark.value, label.id);
    } else if (!props.onlyAssign) {
        actions.value!.showActions({locateTop: locateTop.value})
    } else {
        if (isAssigned(label.id) && !isPrimary(label)) {
            android.setAsPrimaryLabel(bookmark.value, label.id);
        } else {
            android.toggleBookmarkLabel(bookmark.value, label.id);
        }
    }
}

function openActions() {
    actions.value!.showActions()
}

defineExpose({openActions});
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.icon {
  font-size: 10px;
}

.label {
  cursor: pointer;
  position: relative;
  top: -2px;
  margin-top: 3px;
  margin-right: 3px;
  font-weight: normal;
  color: #e8e8e8;
  font-size: 11px;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  border-width: 2px;
  border-style: solid;
  text-overflow: ellipsis;
  white-space: nowrap;
  overflow: hidden;
  max-width: 150px;

  .night & {
    background-color: black;
    color: #bbbbbb;
  }

  .monochrome & {
    background-color: white;
    color: black;
    border-color: black;
    border-style: solid;
    border-width: 1px;
    &.night {
      background-color: white;
      color: black;
      border-color: black;
    }
  }

  &.notAssigned {
    border-style: solid;

    background-color: white;
    color: black;

    .night & {
      background-color: black;
      color: #bbbbbb;
    }

  }

  border-color: rgba(0, 0, 0, 0);
}

.label-list {
  line-height: 0.9em;
  display: inline-flex;
  flex-wrap: wrap;

  &.singleLine {
    flex-wrap: nowrap;
  }
}
</style>
