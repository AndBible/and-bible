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

<script>
import Verse from "@/components/OSIS/Verse";
import W from "@/components/OSIS/W";
import Div from "@/components/OSIS/Div";
import Chapter from "@/components/OSIS/Chapter";
import Reference from "@/components/OSIS/Reference";
import Note from "@/components/OSIS/Note";
import TransChange from "@/components/OSIS/TransChange";
import DivineName from "@/components/OSIS/DivineName";
import Seg from "@/components/OSIS/Seg";
import Milestone from "@/components/OSIS/Milestone";
import Title from "@/components/OSIS/Title";
import Q from "@/components/OSIS/Q";
import Hi from "@/components/OSIS/Hi";
import CatchWord from "@/components/OSIS/CatchWord";
import Item from "@/components/OSIS/Item";
import List from "@/components/OSIS/List";
import P from "@/components/OSIS/P";
import Cell from "@/components/OSIS/Cell";
import L from "@/components/OSIS/L";
import Lb from "@/components/OSIS/Lb";
import Lg from "@/components/OSIS/Lg";
import Row from "@/components/OSIS/Row";
import Table from "@/components/OSIS/Table";
import {h, provide, ref} from "@vue/runtime-core";

const components = {
  Verse, W, Div, Chapter, Reference, Note, TransChange,
  DivineName, Seg, Milestone, Title, Q, Hi, CatchWord, List, Item, P,
  Cell, L, Lb, Lg, Row, Table
}

function prefixComponents() {
  const result = {}
  for(const name in components) {
      result["Osis" + name] = components[name]
  }
  return result;
}

export default {
  name: "OsisFragment",
  props: {
    content: {
      type: String,
      required: true,
    }
  },
  setup() {
    const elementCount = ref(0);
    provide("elementCount", elementCount);
  },
  render() {
    return h({
      template: this.content
          .replace(/(<\/?)(\w)(\w*)([^>]*>)/g,
              (m, tagStart, tagFirst, tagRest, tagEnd) =>
                  `${tagStart}Osis${tagFirst.toUpperCase()}${tagRest}${tagEnd}`),
      components: prefixComponents(components),
    });
  },
}
</script>
