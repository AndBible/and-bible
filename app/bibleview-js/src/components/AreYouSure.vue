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
  <Modal v-if="show" @close="show=false">
    <slot/>
    <template #footer>
      <button class="button" @click="ok">{{strings.ok}}</button>
      <button class="button" @click="cancel">{{strings.cancel}}</button>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/Modal";
import {ref} from "@vue/reactivity";
import {useCommon} from "@/composables";
import {Deferred} from "@/utils";
export default {
  name: "AreYouSure.vue",
  components: {Modal},
  setup() {
    const show = ref(false);
    let promise = null;
    async function areYouSure() {
      show.value = true;
      promise = new Deferred();
      const result = await promise.wait()
      show.value = false;
      return result;
    }
    function ok() {
      promise.resolve(true);
    }
    function cancel() {
      promise.resolve(false);
    }
    return {show, areYouSure, ok, cancel, ...useCommon()};
  }
}
</script>

<style scoped>

</style>
