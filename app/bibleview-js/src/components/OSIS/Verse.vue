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
  <div :style="bookmarkStyle" class="verse bookmarkStyle" :id="`v-${ordinal}`" :class="{noLineBreak: !config.showVersePerLine}">
    <VerseNumber v-if="shown && config.showVerseNumbers && verse !== 0" :verse-num="verse"/>
    <div class="inlineDiv" ref="contentTag"><slot/></div>
  </div>
</template>

<script>
import {inject, provide, reactive, ref} from "@vue/runtime-core";
import VerseNumber from "@/components/VerseNumber";
import {useCommon} from "@/composables";
import {getVerseInfo} from "@/utils";
import highlightRange from "dom-highlight-range";
import {sortBy, sortedUniq, uniqWith} from "lodash";

export default {
  name: "Verse",
  components: {VerseNumber},
  props: {
    osisID: { type: String, required: true},
    verseOrdinal: { type: String, required: true},
  },
  setup(props) {
    const verseInfo = getVerseInfo(props);

    const shown = ref(true);
    verseInfo.showStack = reactive([shown]);
    const {bookmarks, bookmarkLabels} = inject("bookmarks");
    const {fragmentKey} = inject("fragmentInfo");
    provide("verseInfo", verseInfo);
    const common = useCommon();

    return {shown, fragmentKey, ...common, globalBookmarks: bookmarks, globalBookmarkLabels: bookmarkLabels}
  },
  computed: {
    bookmarks: ({globalBookmarks, ordinal}) =>
        Array.from(globalBookmarks.values())
            .filter(({ordinalRange}) => (ordinalRange[0] <= ordinal) && (ordinal <= ordinalRange[1])),
    bookmarkLabels({bookmarks, globalBookmarkLabels}) {
      const labels = new Set();
      for(const b of bookmarks) {
        for(const l of b.labels) {
          labels.add(l);
        }
      }
      return Array.from(labels).map(l => globalBookmarkLabels.get(l)).filter(v => v);
    },
    bookmarkStyle({bookmarkLabels}) {
      return this.styleForLabels(bookmarkLabels)
    },
    styleRanges({bookmarks, leq}) {
      let splitPoints = [];
      for(const b of bookmarks) {
        splitPoints.push(b.elementRange[0])
        splitPoints.push(b.elementRange[1])
      }
      splitPoints = uniqWith(
          sortBy(splitPoints, [v => v[0], v => v[1]]),
          (v1, v2) => v1[0] === v2[0] && v1[1] === v2[1]
      );

      const styleRanges = [];
      for(let i = 0; i < splitPoints.length-1; i++) {
        const elementRange = [splitPoints[i], splitPoints[i+1]];
        const [r1, r2] = elementRange;
        const labels = new Set();
        const bookmarksSet = new Set();
        bookmarks
            .filter(
            b => {
              const [b1, b2] = b.elementRange;
              // Same comparison as in kotlin side BookmarksDao.bookmarksForVerseRange
              return (
                  (leq(r1, b1) && leq(b1, r2))
                  || (leq(r1, b2) && leq(b2, r2))
                  || (leq(b1, r2) && leq(b2, r1))
                  || (leq(b1, r1) && leq(r1, b2) && leq(b1, r2) && leq(r2, b2))
              );
            })
            .forEach(b => {
              bookmarksSet.add(b.id);
              b.labels.forEach(l => labels.add(l))
            });

        styleRanges.push({
          elementRange,
          labels,
          bookmarks: bookmarksSet,
        });
      }
      return styleRanges;
    },
    ordinal() {
      return parseInt(this.verseOrdinal);
    },
    book() {
      return this.osisID.split(".")[0]
    },
    chapter() {
      return parseInt(this.osisID.split(".")[1])
    },
    verse() {
      return parseInt(this.osisID.split(".")[2])
    },
  },
  //watch: {
  //  bookmarks(newBookmarks, oldBoookmarks) {
  //    for(const b of newBookmarks) {
  //      this.highlight(b);
  //    }
  //  }
  //},
  methods: {
    leq([v11, v12], [v21, v22]) {
      if(v11 < v21) return true
      if(v11 === v21) return v12 <= v22;
      return false;
    },
    highlight(bookmark) {
      const [[startCount, startOff], [endCount, endOff]] = bookmark.elementRange;
      //const [startOff, endOff] = bookmark.offsetRange;
      const first = document.querySelector(`.frag-${this.fragmentKey} > [data-element-count="${startCount}"]`).childNodes[0];
      const second = document.querySelector(`.frag-${this.fragmentKey} > [data-element-count="${endCount}"]`).childNodes[0];
      const range = new Range();
      range.setStart(first, startOff);
      range.setEnd(second, endOff);
      bookmark.undoHighlight = highlightRange(range, 'span', { class: `highlighted-${bookmark.id}` });
    },
    styleForLabels(bookmarkLabels) {
      let colors = [];
      for(const s of bookmarkLabels) {
        const c = `rgba(${s.color[0]}, ${s.color[1]}, ${s.color[2]}, 15%)`
        colors.push(c);
      }
      if(colors.length === 1) {
        colors.push(colors[0]);
      }
      const span = 100/colors.length;
      const colorStr = colors.map((v, idx) => {
        let percent;
        if (idx === 0) {
          percent = `${span}%`
        } else if (idx === colors.length - 1) {
          percent = `${span * (colors.length - 1)}%`
        } else {
          percent = `${span * idx}% ${span * (idx + 1)}%`
        }
        return `${v} ${percent}`;
      }).join(", ")

      return `background-image: linear-gradient(to bottom, ${colorStr})`;
    }
  }
}
</script>

<style scoped>
.noLineBreak {
  display: inline;
}

.bookmarkStyle {
  border-radius: 0.2em;
}
</style>
