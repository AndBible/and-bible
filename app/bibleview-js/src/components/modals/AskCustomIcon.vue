<template>
  <ModalDialog v-if="show" @close="selectIcon(selectedIcon)" blocking locate-top>
    <template #title>
      <slot name="title">
        {{ strings.selectCustomIconTitle }}
      </slot>
    </template>
    <div class="icon-list">
      <div
        v-for="[key, icon] in Array.from(customIconMap.entries())"
        :key="key"
        class="icon-item"
        :class="{selected: key === selectedIcon}"
        @click="selectIcon(key)">
        <FontAwesomeIcon :icon="icon" />
      </div>
      <div
         class="icon-item"
         :class="{selected: selectedIcon === null}"
         @click="selectIcon(null)">
         <FontAwesomeIcon :icon="faTimes" />
      </div>
    </div>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import ModalDialog from "@/components/modals/ModalDialog.vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { Deferred } from "@/utils";
import { useCommon } from "@/composables";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import {customIconMap} from "@/composables/fontawesome";

const { strings } = useCommon();

const show = ref(false);
const selectedIcon = ref<null | string>(null);
let deferred: Deferred<null | string> | null = null;

function selectIcon(key: null | string) {
  selectedIcon.value = key;
  deferred?.resolve(selectedIcon.value);
  show.value = false;
}

async function askCustomIcon(current: null | string): Promise<null | string> {
  selectedIcon.value = current ?? null;
  show.value = true;
  deferred = new Deferred<null | string>();
  const result = await deferred.wait();
  return result ?? null;
}

defineExpose({ askCustomIcon });
</script>

<style scoped lang="scss">
@import "~@/common.scss";

.icon-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 20px 0;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  border: 1px solid #ccc;
  cursor: pointer;
  border-radius: 5px;
  
  span {
    margin-top: 5px;
    font-size: 12px;
  }
  
  &.selected {
    border-color: #007bff;
    background-color: #e7f1ff;
  }
  
  .night & {
    border: 1px solid #555;
    background-color: #222;
    
    span {
      color: #ccc;
    }
    
    &.selected {
      border-color: #1e90ff;
      background-color: #333;
    }
  }
}
</style>
