<!--
  - Copyright (c) 2021-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
  <div class="strongs-layout" :class="{ 'two-column': hasBothColumns }">
    <div class="strongs-column" v-if="strongsEntries.length > 0">
      <TabNavigation
          v-if="strongsDictionaries.size > 1"
          :tabs="strongsTabsConfig"
          :active-tab="selectedStrongsDict ?? ''"
          @tab-change="handleStrongsDictChange"
      />
      <div v-for="[strongsKey, fragments] in filteredStrongsEntries" :key="strongsKey" class="strongs-group">
        <div class="strongs-header">
          <span class="strongs-number">{{ strongsKey }}</span>
        </div>
        <div v-for="frag in fragments" :key="frag.key" class="strongs-entry">
          <div v-if="fragments.length > 1" class="dict-label">{{ frag.bookAbbreviation }}</div>
          <OsisFragment hide-titles :fragment="frag"/>
        </div>
        <div class="find-all" v-if="findAllLink(fragments[0])">
          <a :href="findAllLink(fragments[0])!">{{ strings.findAllOccurrences }}</a>
        </div>
        <div v-if="filteredStrongsEntries.length > 1" class="separator"/>
      </div>
    </div>

    <div class="morph-column" v-if="morphFragments.length > 0">
      <TabNavigation
          v-if="morphDictionaries.size > 1"
          :tabs="morphTabsConfig"
          :active-tab="selectedMorphDict ?? ''"
          @tab-change="handleMorphDictChange"
      />
      <div v-for="frag in filteredMorphFragments" :key="frag.key" class="morph-entry">
        <div class="morph-header">
          <span class="morph-code">{{ frag.keyName }}</span>
        </div>
        <OsisFragment hide-titles :fragment="frag"/>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useCommon} from "@/composables";
import OsisFragment from "@/components/documents/OsisFragment.vue";
import TabNavigation from "@/components/tabs/TabNavigation.vue";
import type {Tab} from "@/components/tabs/TabContainer.vue";
import {computed, ref, watch} from "vue";
import {OsisFragment as OsisFragmentType} from "@/types/client-objects";
import {MultiFragmentDocument} from "@/types/documents";

const props = defineProps<{ document: MultiFragmentDocument }>();

const {strings, android} = useCommon();

const strongsEntries = computed(() => {
    const groups = new Map<string, OsisFragmentType[]>();
    for (const frag of props.document.osisFragments) {
        if (frag.features?.type) {
            const prefix = frag.features.type === "hebrew" ? "H" : "G";
            const key = `${prefix}${frag.features.keyName}`;
            if (!groups.has(key)) {
                groups.set(key, []);
            }
            groups.get(key)!.push(frag);
        }
    }
    return [...groups.entries()];
});

const morphFragments = computed(() => {
    return props.document.osisFragments.filter(frag => !frag.features?.type);
});

const strongsDictionaries = computed(() => {
    const dicts = new Map<string, string>();
    for (const [, fragments] of strongsEntries.value) {
        for (const frag of fragments) {
            if (!dicts.has(frag.bookInitials)) {
                dicts.set(frag.bookInitials, frag.bookAbbreviation);
            }
        }
    }
    return dicts;
});

const morphDictionaries = computed(() => {
    const dicts = new Map<string, string>();
    for (const frag of morphFragments.value) {
        if (!dicts.has(frag.bookInitials)) {
            dicts.set(frag.bookInitials, frag.bookAbbreviation);
        }
    }
    return dicts;
});

const strongsTabsConfig = computed<Tab[]>(() =>
    [...strongsDictionaries.value.entries()].map(([initials, abbreviation]) => ({
        id: initials,
        label: abbreviation,
    }))
);

const morphTabsConfig = computed<Tab[]>(() =>
    [...morphDictionaries.value.entries()].map(([initials, abbreviation]) => ({
        id: initials,
        label: abbreviation,
    }))
);

function initialStrongsDict(): string | undefined {
    return props.document.state?.selectedStrongsDict
        ?? strongsDictionaries.value.keys().next().value;
}

function initialMorphDict(): string | undefined {
    return props.document.state?.selectedMorphDict
        ?? morphDictionaries.value.keys().next().value;
}

const selectedStrongsDict = ref<string | undefined>(initialStrongsDict());
const selectedMorphDict = ref<string | undefined>(initialMorphDict());

// Reset selection when available dictionaries change (e.g. hebrew→greek)
watch(strongsDictionaries, (dicts) => {
    if (selectedStrongsDict.value && !dicts.has(selectedStrongsDict.value)) {
        selectedStrongsDict.value = dicts.keys().next().value;
    }
});

watch(morphDictionaries, (dicts) => {
    if (selectedMorphDict.value && !dicts.has(selectedMorphDict.value)) {
        selectedMorphDict.value = dicts.keys().next().value;
    }
});

function handleStrongsDictChange(tabId: string) {
    selectedStrongsDict.value = tabId;
}

function handleMorphDictChange(tabId: string) {
    selectedMorphDict.value = tabId;
}

const filteredStrongsEntries = computed(() => {
    if (strongsDictionaries.value.size <= 1) return strongsEntries.value;
    const sel = selectedStrongsDict.value;
    return strongsEntries.value
        .map(([key, fragments]) => [key, fragments.filter(f => f.bookInitials === sel)] as [string, OsisFragmentType[]])
        .filter(([, fragments]) => fragments.length > 0);
});

const filteredMorphFragments = computed(() => {
    if (morphDictionaries.value.size <= 1) return morphFragments.value;
    const sel = selectedMorphDict.value;
    return morphFragments.value.filter(f => f.bookInitials === sel);
});

const hasBothColumns = computed(() => {
    return strongsEntries.value.length > 0 && morphFragments.value.length > 0;
});

function saveState() {
    android.saveState({
        selectedStrongsDict: selectedStrongsDict.value,
        selectedMorphDict: selectedMorphDict.value,
    });
}

watch(selectedStrongsDict, saveState);
watch(selectedMorphDict, saveState);

function findAllLink(frag: OsisFragmentType): string | null {
    const {type: featureType = null, keyName: featureKeyName = null} = frag.features;
    return featureType ? `ab-find-all://?type=${featureType}&name=${featureKeyName}` : null;
}
</script>

<style scoped lang="scss">
@use "@/common.scss" as *;

.strongs-layout {
  &.two-column {
    @media (min-width: 600px) {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 2em;
    }
  }
}

.strongs-group {
  margin-bottom: 0.5em;
}

.strongs-header {
  font-weight: bold;
  font-size: 1.1em;
  margin-bottom: 0.15em;
}

.dict-label {
  font-size: 0.8em;
  opacity: 0.6;
  .monochrome & {
    opacity: 1;
  }
}

.strongs-entry {
  margin-bottom: 0.15em;
}

.find-all {
  text-align: right;
  font-size: 0.9em;
  padding-top: 0.15em;
}

.morph-header {
  font-weight: bold;
  margin-bottom: 0.15em;
}

.morph-entry {
  margin-bottom: 0.35em;
}
</style>
