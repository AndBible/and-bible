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
  <Modal v-if="show" @close="show=false" blocking locate-top>
    <template #title>
      <slot name="title"/>
    </template>
    <slot/>
    <template #footer>
      <button class="button" @click="cancel">{{strings.cancel}}</button>
      <button v-for="b in buttons" :key="b.result" class="button" :class="b.class" @click="buttonClicked(b.result)">{{b.title}}</button>
    </template>
  </Modal>
</template>

<script>
import Modal from "@/components/modals/Modal";
import {ref} from "vue";
import {useCommon} from "@/composables";
import {Deferred} from "@/utils";
export default {
  name: "AreYouSure",
  components: {Modal},
  setup() {
    const show = ref(false);
    let promise = null;
    const {strings, ...common} = useCommon();

    const okButton = {
      title: strings.yes,
      class: "warning",
      result: true
    }

    const buttons = ref(null);

    async function areYouSure(btns = [okButton]) {
      buttons.value = btns;
      show.value = true;
      promise = new Deferred();
      const result = await promise.wait()
      show.value = false;
      return result;
    }
    function buttonClicked(result) {
      promise.resolve(result);
    }
    function cancel() {
      promise.resolve(false);
    }
    return {show, areYouSure, buttonClicked, cancel, strings, common, buttons};
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
</style>
