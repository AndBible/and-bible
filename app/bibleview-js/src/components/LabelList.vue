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
  <AmbiguousSelection blocking ref="ambiguousSelection"/>
  <div class="label-list">
    <div
      @touchstart="labelClicked($event, label)"
      @click="labelClicked($event, label)"
      v-for="label in labels"
      :key="label.id"
      :style="labelStyle(label)"
      class="label"
      :class="{notAssigned: !isAssigned(label.id)}"
    >
      <span v-if="isPrimary(label)" class="icon"><FontAwesomeIcon icon="bookmark"/></span>
      {{label.name}}
    </div>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {inject} from "@vue/runtime-core";
import {computed, ref} from "@vue/reactivity";
import {addEventFunction, Deferred} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {sortBy} from "lodash";

export default {
  props: {
    bookmarkId: {type: Number, required: true},
    handleTouch: {type: Boolean, default: false},
    disableLinks: {type: Boolean, default: false},
  },
  components: {FontAwesomeIcon},
  name: "LabelList",
  setup(props) {
    const {adjustedColor, strings, ...common} = useCommon();
    const ambiguousSelection = ref(null);
    const appSettings = inject("appSettings");
    const android = inject("android");

    function labelStyle(label) {
      const color = adjustedColor(label.color).string();
      if (isAssigned(label.id)) {
        return `background-color: ${color};`;
      } else {
        return `border-color: ${color};`;
      }
    }

    const {bookmarkMap, bookmarkLabels} = inject("globalBookmarks");
    const bookmark = computed(() => bookmarkMap.get(props.bookmarkId));

    function isAssigned(labelId) {
      return bookmark.value.labels.includes(labelId);
    }

    const labels = computed(() => {
      const labels = bookmark.value.labels.slice();
      const favs = appSettings.favouriteLabels.filter(l => !labels.includes(l))
      const lbls = [...labels, ...favs].map(labelId => bookmarkLabels.get(labelId));
      return sortBy(lbls, ["name"]);
    });

    function assignLabels() {
      if(bookmark.value) {
        android.assignLabels(bookmark.value.id);
      }
    }

    let clickDeferred = null;

    async function labelClicked(event, label) {
      if(props.disableLinks) return;
      if(event.type === "touchstart" && !props.handleTouch) {
        return;
      }
      event.stopPropagation();

      if(props.handleTouch) {
        if(event.type === "click") {
          if (clickDeferred) {
            clickDeferred.resolve();
            clickDeferred = null;
          } else {
            console.error("Deferred not found");
          }
          return
        }
        else if(event.type === "touchstart") {
          clickDeferred = new Deferred();
          await clickDeferred.wait();
        }
      }

      if(!isAssigned(label.id)) {
        addEventFunction(event, () => {
          android.toggleBookmarkLabel(bookmark.value.id, label.id);
        }, {title: strings.addBookmarkLabel});
      } else {
        if (label.isRealLabel) {
          if (bookmark.value.primaryLabelId !== label.id) {
            addEventFunction(event, () => {
              android.setAsPrimaryLabel(bookmark.value.id, label.id);
            }, {title: strings.setAsPrimaryLabel});
          }
          addEventFunction(event, () => {
            android.toggleBookmarkLabel(bookmark.value.id, label.id);
          }, {title: strings.removeBookmarkLabel});
        }
        addEventFunction(event, assignLabels, {title: strings.assignLabelsMenuEntry})
      }
      ambiguousSelection.value.handle(event);
    }
    function isPrimary(label) {
      return label.id === bookmark.value.primaryLabelId;
    }
    return {labelStyle, assignLabels, ambiguousSelection, labelClicked, labels, isPrimary, isAssigned, ...common}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.icon {
  font-size: 10px;
}
.label {
  $padding: 2px;
  height: calc(12px + #{2*$padding});
  padding-top: $padding;
  font-weight: normal;
  color: #e8e8e8;
  font-size: 11px;
  border-radius: 6pt;
  padding-left: 4pt;
  padding-right: 4pt;
  margin-right: 2pt;
  border-width: 2px;
  border-style: solid;
  background-color: rgba(0, 0, 0, 0.2);
  .night & {
    background-color: rgba(255, 255, 255, 0.2);
  }
  &.notAssigned {
    border-style: dotted;
  }
  border-color: rgba(0, 0, 0, 0);
}
.label-list {
  line-height: 1em;
  display: inline-flex;
  flex-wrap: wrap;
}
</style>
