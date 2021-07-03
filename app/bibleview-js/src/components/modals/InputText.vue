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
  <Modal v-if="show" @close="show=false" blocking>
    <template #title>
      <slot/>
    </template>
    <template #extra-buttons>
      <slot name="buttons"/>
    </template>
    <slot name="content">
      <input class="text-input" ref="inputElement" :placeholder="strings.inputPlaceholder" v-model="text"/>
      <div v-if="error" class="error">{{ error }}</div>
    </slot>
    <template #footer>
      <button class="button" @click="ok">{{strings.ok}}</button>
      <button class="button" @click="cancel">{{strings.cancel}}</button>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {Deferred} from "@/utils";
import {nextTick} from "@vue/runtime-core";
export default {
  name: "InputText",
  components: {Modal},
  setup() {
    const text = ref("");
    const error = ref("");
    const show = ref(false);
    const inputElement = ref(null);
    let promise = null;
    async function inputText(initialValue="", _error = "") {
      text.value =initialValue;
      error.value = _error;
      show.value = true;
      promise = new Deferred();
      await nextTick();
      inputElement.value.focus();
      const result = await promise.wait()
      show.value = false;
      return result;
    }
    function ok() {
      promise.resolve(text.value);
    }
    function cancel() {
      promise.resolve(null);
    }
    return {show, inputText, ok, cancel, text, error, inputElement, ...useCommon()};
  }
}
</script>

<style scoped>
.text-input {
  padding: 5pt;
  margin: 5pt;
}
.error {
  color:red;
}
</style>
