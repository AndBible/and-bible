<!--
  - Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div class="overlay-scrollbar" aria-hidden="true">
    <div
        v-if="thumb"
        class="overlay-scrollbar-thumb"
        :class="{dragging: dragStart !== null}"
        :style="{height: `${thumb.height}px`, transform: `translateY(${thumb.top}px)`}"
        @pointerdown="onPointerDown"
    />
  </div>
</template>

<script setup lang="ts">
// Thin document scrollbar for the iOS host (iPhone, iPad, iOS on Mac), the counterpart of the desktop host's
// ::-webkit-scrollbar rules that WKWebView does not honour (issue #4137). It only shows position; a touch passes
// through to the page (CSS keeps the thumb inert on touch screens, and touches are ignored here), while a mouse or
// trackpad pointer can drag it.
import {onBeforeUnmount, onMounted, ref, watch} from "vue";
import {ScrollbarInput, scrollTopForThumbTop, ThumbGeometry, thumbGeometry} from "@/code/overlay-scrollbar";

const props = withDefaults(defineProps<{ topInset?: number; bottomInset?: number }>(), {topInset: 0, bottomInset: 0});

const MIN_THUMB = 24;
const thumb = ref<ThumbGeometry | null>(null);
const dragStart = ref<{ pointerId: number; clientY: number; thumbTop: number } | null>(null);

function input(): ScrollbarInput {
    const viewportHeight = window.innerHeight;
    return {
        viewportHeight,
        scrollHeight: document.documentElement.scrollHeight,
        trackTop: props.topInset,
        trackHeight: viewportHeight - props.topInset - props.bottomInset,
        minThumb: MIN_THUMB,
    };
}

function update() {
    thumb.value = thumbGeometry({...input(), scrollTop: window.scrollY});
}

let frame: number | null = null;
function scheduleUpdate() {
    if (frame !== null) return;
    frame = requestAnimationFrame(() => {
        frame = null;
        update();
    });
}

function onPointerDown(e: PointerEvent) {
    if (e.pointerType !== "mouse" || e.button !== 0 || !thumb.value) return;
    e.preventDefault();
    dragStart.value = {pointerId: e.pointerId, clientY: e.clientY, thumbTop: thumb.value.top};
}

function onPointerMove(e: PointerEvent) {
    const start = dragStart.value;
    if (!start || e.pointerId !== start.pointerId) return;
    e.preventDefault?.();
    window.scrollTo(window.scrollX, scrollTopForThumbTop(input(), start.thumbTop + e.clientY - start.clientY));
    update();
}

function onPointerUp(e: PointerEvent) {
    if (dragStart.value && e.pointerId === dragStart.value.pointerId) dragStart.value = null;
}

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
    window.addEventListener("scroll", scheduleUpdate, {passive: true});
    window.addEventListener("resize", scheduleUpdate);
    window.addEventListener("pointermove", onPointerMove);
    window.addEventListener("pointerup", onPointerUp);
    window.addEventListener("pointercancel", onPointerUp);
    // Infinite scroll grows the page without a scroll event.
    if (typeof ResizeObserver !== "undefined") {
        resizeObserver = new ResizeObserver(scheduleUpdate);
        resizeObserver.observe(document.body);
    }
    update();
});

watch(() => [props.topInset, props.bottomInset], update);

onBeforeUnmount(() => {
    window.removeEventListener("scroll", scheduleUpdate);
    window.removeEventListener("resize", scheduleUpdate);
    window.removeEventListener("pointermove", onPointerMove);
    window.removeEventListener("pointerup", onPointerUp);
    window.removeEventListener("pointercancel", onPointerUp);
    resizeObserver?.disconnect();
    if (frame !== null) cancelAnimationFrame(frame);
});
</script>

<style lang="scss">
.overlay-scrollbar {
  position: fixed;
  top: 0;
  right: 0;
  width: 8px;
  height: 100%;
  z-index: 6;
  pointer-events: none;
}

// Same look as the desktop host's ::-webkit-scrollbar (common.scss): grey with alpha reads in day, night and
// monochrome. No transitions, so the no-animations setting needs nothing.
.overlay-scrollbar-thumb {
  position: absolute;
  top: 0;
  right: 1px;
  width: 6px;
  border-radius: 3px;
  background-color: rgba(128, 128, 128, .45);
  pointer-events: none;
  touch-action: none;
  &.dragging {
    background-color: rgba(96, 96, 96, .7);
  }
  // A mouse or trackpad pointer (iOS on Mac, iPad with a trackpad) can grab it; on a touch-only screen the
  // thumb stays inert, so a tap near the right edge still turns the page and a swipe still scrolls.
  @media (any-hover: hover) and (any-pointer: fine) {
    pointer-events: auto;
    cursor: default;
    &:hover {
      background-color: rgba(96, 96, 96, .7);
    }
  }
}
</style>
