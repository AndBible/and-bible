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
  <div class="features-link" v-if="featuresLink">
    <a :href="featuresLink">{{strings.findAllOccurrences}}</a>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import {computed} from "vue";

export default {
  name: "FeaturesLink",
  props: {fragment: {type: Object, required: true}},
  setup(props) {
    const featuresLink = computed(() => {
      const {type: featureType = null, keyName: featureKeyName = null} = props.fragment.features;
      return featureType ? `ab-find-all://?type=${featureType}&name=${featureKeyName}` : null;
    })
    return {featuresLink, ...useCommon()}
  }
}
</script>

<style scoped>
.features-link {
  padding-top: 1em;
  text-align: right;
}
</style>
