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
  <h2 v-if="document.compare">{{ osisFragments[0].keyName }}</h2>
  <div v-for="(fragment, index) in filteredOsisFragments" :key="fragment.key">
    <div class="ref-link">
      <div class="flex">
        <a :href="link(fragment, document.compare)">
          <template v-if="document.compare">{{ fragment.bookAbbreviation }}</template>
          <template v-else>{{ sprintf(strings.multiDocumentLink, fragment.keyName, fragment.bookAbbreviation) }}
          </template>
        </a>
        <div v-if="document.compare && !exportMode" class="hide-button"
             @click="android.toggleCompareDocument(fragment.bookInitials)">
          <FontAwesomeIcon icon="eye-slash"/>
        </div>
      </div>
    </div>
    <OsisFragment hide-titles :fragment="fragment"/>
    <FeaturesLink :fragment="fragment"/>
    <div v-if="index < filteredOsisFragments.length - 1" class="separator"/>
  </div>
  <div class="restore" v-if="document.compare && hiddenOsisFragments.length > 0 && !exportMode">
    <div class="separator"/>
    <div class="flex2">
      <div class="restore-button">
        <FontAwesomeIcon icon="eye"/>
      </div>
      <a @click="android.toggleCompareDocument(fragment.bookInitials)" v-for="fragment  in hiddenOsisFragments"
         :key="fragment.key">
        {{ fragment.bookAbbreviation }} &nbsp;
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import {computed, inject, ref} from "vue";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import FeaturesLink from "@/components/FeaturesLink.vue";
import {appSettingsKey, exportModeKey} from "@/types/constants";
import {OsisFragment as OsisFragmentType} from "@/types/client-objects";
import {MultiFragmentDocument} from "@/types/documents";
import {formatExportLink} from "@/utils";

const props = defineProps<{ document: MultiFragmentDocument }>();

// eslint-disable-next-line vue/no-setup-props-destructure
const {osisFragments} = props.document;
const exportMode = inject(exportModeKey, ref(false));
const appSettings = inject(appSettingsKey)!;

const filteredOsisFragments = computed(() => {
    if (props.document.compare) {
        return osisFragments.filter(v => !appSettings.hideCompareDocuments.includes(v.bookInitials))
    } else {
        return osisFragments;
    }
});
const hiddenOsisFragments = computed(() => {
    return osisFragments.filter(v => appSettings.hideCompareDocuments.includes(v.bookInitials))
});

function link(frag: OsisFragmentType, compare = false) {
    const isBible = frag.bookCategory === "BIBLE"
    const osis = (compare || !isBible) ? encodeURI(frag.osisRef) + "&doc=" + encodeURI(frag.bookInitials) + "&force-doc" : encodeURI(frag.osisRef);
    if(exportMode.value) {
        return formatExportLink({ref: osis, v11n: frag.v11n})
    } else {
        return `osis://?osis=${osis}&v11n=${frag.v11n}`;
    }
}

const {android, sprintf, strings} = useCommon();
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
  justify-content: flex-start;
  flex-wrap: wrap;
}

.hide-button {
  justify-self: end;
  font-size: 120%;
  color: $modal-header-background-color;
}

.restore-button {
  justify-self: start;
  font-size: 120%;
  color: $modal-header-background-color;
}
</style>
