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
      <a :href="link(fragment)">
        <template v-if="document.compare">{{fragment.bookAbbreviation}}</template>
        <template v-else>{{sprintf(strings.multiDocumentLink, fragment.keyName, fragment.bookInitials)}}</template>
      </a>
      <template v-if="document.compare">
        (<a @click="android.toggleCompareDocument(fragment.bookInitials)">hide</a>)
      </template>
    </div>
    <OsisFragment hide-titles :fragment="fragment"/>
    <div v-if="index < filteredOsisFragments.length - 1" class="separator"/>
  </div>
  <div class="restore" v-if="document.compare && hiddenOsisFragments.length > 0">
    <div class="separator"/>
    {{ strings.restoreCompareTitle }}
    <a @click="android.toggleCompareDocument(fragment.bookInitials)" v-for="fragment  in hiddenOsisFragments" :key="fragment.key">
      {{ fragment.bookAbbreviation }} &nbsp;
    </a>
  </div>
</template>

<script>
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment";
import {inject} from "@vue/runtime-core";
import {computed} from "@vue/reactivity";

export default {
  name: "MultiDocument",
  components: {OsisFragment},
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
.ref-link {
  padding-bottom: 0.5em;
  font-weight: bold;
}
.separator {
  height: 1pt;
  width: calc(100% - 10pt);
  margin: 10pt 5pt 5pt;
  background: rgba(0, 0, 0, 0.2);
}
.restore {
  a {
    padding-inline-start: 0.5em;
  }
}

</style>
