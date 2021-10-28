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
  <teleport to="#modals">
    <div v-if="blocking" @click.stop="$emit('close')" class="modal-backdrop"/>
    <div :class="{blocking}">
      <div ref="modal" @click.stop class="modal-content" :class="{blocking, wide, edit, limit}"
      >
        <div ref="header" class="modal-header">
          <slot name="title-div">
            <div class="title">
              <slot name="title"/>
            </div>
          </slot>
          <div class="modal-toolbar">
            <slot name="buttons">
              <slot name="extra-buttons"/>
              <button class="modal-action-button right" @touchstart.stop @click.stop="$emit('close')">
                <FontAwesomeIcon icon="times"/>
              </button>
            </slot>
          </div>
        </div>
        <div v-if="ready" class="modal-body">
          <slot/>
        </div>
        <div v-if="$slots.footer" class="modal-footer">
          <div class="modal-footer-buttons">
            <slot name="footer"/>
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>
<script>

import {inject, nextTick, onMounted, onUnmounted} from "@vue/runtime-core";
import {useCommon} from "@/composables";
import {ref} from "@vue/reactivity";
import {
  draggableElement,
  setupDocumentEventListener,
  setupWindowEventListener,
} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

export default {
  name: "Modal",
  emits: ["close"],
  props: {
    blocking: {type: Boolean, default: false},
    wide: {type: Boolean, default: false},
    edit: {type: Boolean, default: false},
    locateTop: {type: Boolean, default: false},
    limit: {type: Boolean, default: false},
  },
  components: {FontAwesomeIcon},
  setup: function (props, {emit}) {
    const config = inject("config");
    const modal = ref(null);
    const header = ref(null);
    const ready = ref(false);

    async function resetPosition(horizontal = false) {
      if(horizontal) {
        modal.value.style.left = `var(--modal-left)`;
      }

      if(props.locateTop) {
        modal.value.style.top = `calc(var(--top-offset) + var(--modal-top))`;
      } else {
        modal.value.style.top = null;
        modal.value.style.bottom = `calc(var(--bottom-offset) + var(--modal-top))`;
        await nextTick();
        modal.value.style.top = `${modal.value.offsetTop}px`;
        modal.value.style.bottom = null;
      }
      await nextTick();
      height.value = modal.value.clientHeight;
    }

    const {register} = inject("modal");
    register({blocking: props.blocking, close: () => emit("close")});

    setupWindowEventListener("resize", () => resetPosition(true));
    setupDocumentEventListener("keyup", event => {
      if(event.key === "Escape") {
        emit("close");
      }
    });

    const height = ref(0);

    const observer = new ResizeObserver(() => {
      resetPosition();
    });

    onMounted(async () => {
      await resetPosition(true)
      draggableElement(modal.value, header.value);
      observer.observe(modal.value);
      ready.value = true;
    });

    onUnmounted(() => {
      observer.disconnect();
    });

    return {height, config, modal, header, ready, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.modal-backdrop {
  display: block;
  position: fixed;
  z-index: 10;
  padding-top: 10px;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0,0,0,0.5);
}

$border-radius: 8pt;
$border-radius2: $border-radius - 1.5pt;

.modal-content {
  font-family: sans-serif;
  font-size: 12pt;
  z-index: 5;
  .blocking & {
    z-index: 15;
  }
  position: fixed;
  background-color: $modal-content-background-color;
  padding: 0;
  border: 1px solid #888;
  box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19);
  animation-name: animatetop;
  animation-duration: 0.2s;
  .night & {
    background-color: $modal-content-background-color-night;
    color: #bdbdbd;
  }
  border-radius: $border-radius;
  width: var(--modal-width);

  --modal-left: calc((100% - var(--modal-width)) / 2);
  --modal-width: calc(min(80%, var(--text-max-width)));
  --modal-top: 30px;

  &.wide {
    --modal-width: calc(min(var(--text-max-width) + 20px, 100% - 60px));
    --modal-top: 25px;
  }
}

@keyframes animatetop {
  from {opacity:0}
  to {opacity:1}
}

.title {
  padding-top: 5pt;
  margin-top: 2pt;
}

.modal-header {
  display:flex;
  justify-content: space-between;
  padding: 0.1em;
  padding-left: 0.5em;
  background-color: $modal-header-background-color;
  --header-backround: #{$modal-header-background-color};
  color: white;
  font-weight: bold;
  border-radius: $border-radius2 $border-radius2 0 0;

  .night & {
    background-color: $night-modal-header-background-color;
    --header-backround: #{$night-modal-header-background-color};
    color: #e2e2e2;
  }
}

.modal-toolbar {
  align-self: flex-end;
  display: flex;
}

.modal-body {
  --max-height: calc(100vh - var(--top-offset) - var(--bottom-offset) - 100px);
  .limit & {
    --max-height: min(calc(100vh - var(--top-offset) - var(--bottom-offset) - 100px), 165px);
  }
  //min-height: 60pt;
  padding: 5px 5px;
  margin: 5pt 5pt;
  .edit & {
    padding: 0px 0px;
    margin: 0pt 0pt;
  }
  .night & {
    background-color: $modal-content-background-color-night;
  }
}

.modal-footer {
  border-radius: 0 0 $border-radius2 $border-radius2;
  display: flex;
  flex-direction: row;
  justify-content: space-around;
  padding-top: 2px;
  padding-bottom: 2px;
  background-color: #acacac;
  color: white;
  .night & {
    background-color: #454545;
    color: #bdbdbd;
  }
}

</style>
