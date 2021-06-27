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
  <h2 v-if="document.compare">{{osisFragments[0].keyName}}</h2>
  <div v-for="(fragment, index) in filteredOsisFragments" :key="fragment.key">
    <div class="ref-link">
      <div class="flex">
        <a :href="link(fragment)">
          <template v-if="document.compare">{{fragment.bookAbbreviation}}</template>
          <template v-else>{{sprintf(strings.multiDocumentLink, fragment.keyName, fragment.bookInitials)}}</template>
        </a>
        <div v-if="document.compare" class="hide-button" @click="android.toggleCompareDocument(fragment.bookInitials)">
          <FontAwesomeIcon icon="eye-slash"/>
        </div>
      </div>
    </div>
    <OsisFragment hide-titles :fragment="fragment"/>
    <div v-if="index < filteredOsisFragments.length - 1" class="separator"/>
  </div>
  <div class="restore" v-if="document.compare && hiddenOsisFragments.length > 0">
    <div class="separator"/>
    <div class="flex2">
      <div class="restore-button">
        <FontAwesomeIcon icon="eye"/>
      </div>
      <a @click="android.toggleCompareDocument(fragment.bookInitials)" v-for="fragment  in hiddenOsisFragments" :key="fragment.key">
        {{ fragment.bookAbbreviation }} &nbsp;
      </a>
    </div>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment";
import {inject} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

export default {
  name: "MultiDocument",
  components: {OsisFragment, FontAwesomeIcon},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure
    const {osisFragments} = props.document;

    const appSettings = inject("appSettings");

    const filteredOsisFragments = computed(() => {
      if(props.document.compare) {
        return osisFragments.filter(v => !appSettings.hideCompareDocuments.includes(v.bookInitials))
      } else {
        return osisFragments;
      }
    });
    const hiddenOsisFragments = computed(() => {
      return osisFragments.filter(v => appSettings.hideCompareDocuments.includes(v.bookInitials))
    });

    function link(frag) {
      const osis = encodeURI(`${frag.bookInitials}:${frag.osisRef}`)
      return `osis://?osis=${osis}`
    }

    return {hiddenOsisFragments, filteredOsisFragments, osisFragments, link, ...useCommon()}
  }
}
</script>

<style scoped lang="scss">
@import "~@/common.scss";
.ref-link {
  padding-bottom: 0.5em;
  font-weight: bold;
}
.restore {
  a {
    padding-inline-start: 0.5em;
  }
}
.flex {
  display: flex;
  justify-content: space-between;
}
.flex2 {
  display: flex;
  justify-content: start;
  flex-wrap: wrap;
}

.hide-button {
  justify-self: end;
  font-size: 120%;
  color:$modal-header-background-color;
}
.restore-button {
  justify-self: start;
  font-size: 120%;
  color:$modal-header-background-color;
}
</style>
