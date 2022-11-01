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
  <div>
    <OsisFragment do-not-convert :fragment="osisFragment"/>
    <OpenAllLink :v11n="document.v11n"/>
    <FeaturesLink :fragment="osisFragment"/>
  </div>
</template>

<script>
import OsisFragment from "@/components/documents/OsisFragment";
import FeaturesLink from "@/components/FeaturesLink";
import OpenAllLink from "@/components/OpenAllLink";
import {useReferenceCollector} from "@/composables";
import {BookCategories} from "@/constants";
import {provide} from "vue";
import {osisToTemplateString} from "@/utils";

const parser = new DOMParser();

// https://stackoverflow.com/questions/49836558/split-string-at-space-after-certain-number-of-characters-in-javascript/49836804
const splitRegex = /.{1,100}(\s|$)/g
const spacesRegex = /^\s+$/

export default {
  name: "OsisDocument",
  components: {OsisFragment, FeaturesLink, OpenAllLink},
  props: {
    document: {type: Object, required: true},
  },
  setup(props) {
    // eslint-disable-next-line vue/no-setup-props-destructure,no-unused-vars
    const {osisFragment, bookCategory} = props.document;
    const referenceCollector = useReferenceCollector();

    if(bookCategory === BookCategories.COMMENTARIES || bookCategory === BookCategories.GENERAL_BOOK) {
      provide("referenceCollector", referenceCollector);
    }

    function splitString(s) {
      const v = s.match(splitRegex);
      if(v === null) {
        return [s];
      }
      return v;
    }

    function addAnchors(xml) {
      const xmlDoc = parser.parseFromString(xml, "text/xml");
      const walker = xmlDoc.createTreeWalker(xmlDoc.firstElementChild, NodeFilter.SHOW_TEXT)
      const textNodes = [];
      while(walker.nextNode()) {
        textNodes.push(walker.currentNode);
      }
      let count = 0;

      function addAnchor(node, textNode) {
        if (textNode.textContent.match(spacesRegex)) {
          node.parentElement.insertBefore(textNode, node);
        } else {
          const anchor = xmlDoc.createElement("BWA"); // BibleViewAnchor.vue
          anchor.setAttribute("ordinal", count++);
          anchor.appendChild(textNode)
          node.parentElement.insertBefore(anchor, node);
        }
      }

      for(const node of textNodes) {
        const splitText = splitString(node.textContent).map(t => xmlDoc.createTextNode(t));
        for(let i = 0; i<splitText.length; i++) {
          addAnchor(node, splitText[i])
        }
        node.parentNode.removeChild(node);
      }
      return xmlDoc.firstElementChild.outerHTML;
    }

    let xml = osisFragment.xml
    osisFragment.originalXml = xml;
    xml = osisToTemplateString(xml)
    xml = addAnchors(xml);
    osisFragment.xml = xml;
    return {osisFragment};
  }
}
</script>
