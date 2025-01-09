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
  <teleport to="#modals">
    <div v-if="blocking" @click.stop="$emit('close')" class="modal-backdrop"/>
    <div :class="{blocking}">
      <div
          ref="modal"
          @click.stop
          class="modal-content"
          :class="{blocking, wide, edit, limit}"
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
        <div ref="body" class="modal-body" @click.stop>
          <slot v-if="ready"/>
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
<script setup lang="ts">
import {inject, nextTick, onMounted, onUnmounted, ref, shallowRef, watch} from "vue";
import {useCommon} from "@/composables";
import {draggableElement, setupDocumentEventListener, setupWindowEventListener,} from "@/utils";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {modalKey} from "@/types/constants";

const emit = defineEmits(["close"]);
const props = withDefaults(
    defineProps<{
        blocking: boolean
        wide: boolean
        edit: boolean
        locateTop: boolean
        limit: boolean
    }>(),
    {
        blocking: false,
        wide: false,
        edit: false,
        locateTop: false,
        limit: false
    }
);

const modal = shallowRef<HTMLElement | null>(null);
const header = ref(null);
const ready = ref(false);

async function resetPosition(horizontal = false) {
    const m = modal.value!;
    if (horizontal) {
        m.style.left = `var(--modal-left)`;
    }

    if (props.locateTop) {
        m.style.top = `calc(var(--top-offset) + var(--modal-top))`;
    } else {
        m.style.top = "";
        m.style.bottom = `calc(var(--bottom-offset) + var(--modal-top))`;
        await nextTick();
        m.style.top = `${m.offsetTop}px`;
        m.style.bottom = "";
    }
    await nextTick();
    height.value = m.clientHeight;
}

const {register} = inject(modalKey)!;
register({blocking: props.blocking, close: () => emit("close")});

setupWindowEventListener("resize", () => resetPosition(true));
setupDocumentEventListener("keyup", (event: KeyboardEvent) => {
    if (event.key === "Escape") {
        emit("close");
    }
});

const height = ref(0);

const observer = new ResizeObserver(() => {
    resetPosition();
});

const body = ref<HTMLElement | null>(null);

onMounted(async () => {
    await resetPosition(true)
    const m = modal.value!
    draggableElement(m, header.value!);
    observer.observe(m);
    ready.value = true;
    body.value!.focus();
});

onUnmounted(() => {
    observer.disconnect();
});

const {appSettings} = useCommon()

watch(() => [appSettings.bottomOffset, appSettings.topOffset], () => resetPosition());

defineExpose({height});
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
  background-color: rgba(0, 0, 0, 0.5);
  .monochrome & {
    background-color: unset;
  }
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
  box-shadow: 0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.19);
  animation-name: animatetop;
  animation-duration: 0.2s;
  .noAnimation & {
    animation: none;
    box-shadow: none;
  }

  .night & {
    background-color: $modal-content-background-color-night;
    color: #bdbdbd;
  }

  .monochrome.night & {
    border-color: white;
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
  from {
    opacity: 0
  }
  to {
    opacity: 1
  }
}

.title {
  padding-top: 5pt;
  margin-top: 2pt;
}

.modal-header {
  display: flex;
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
  .monochrome.night & {
    color: white;
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
    padding: 0 0;
    margin: 0 0;
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
