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
import List from "@/components/OSIS/List";
import Item from "@/components/OSIS/Item";
import P from "@/components/OSIS/P";
import Cell from "@/components/OSIS/Cell";
import L from "@/components/OSIS/L";
import Lb from "@/components/OSIS/Lb";
import Lg from "@/components/OSIS/Lg";
import Row from "@/components/OSIS/Row";
import Table from "@/components/OSIS/Table";
import {h} from "@vue/runtime-core";
import Foreign from "@/components/OSIS/Foreign";
import Figure from "@/components/OSIS/Figure";
import A from "@/components/OSIS/A";
import Abbr from "@/components/OSIS/Abbr";
import {osisToTemplateString} from "@/utils";
import BibleViewAnchor from "@/components/BibleViewAnchor";
import AndBibleLink from "@/components/OSIS/AndBibleLink";

const teiComponents = {
  Ref: Reference, Pron: Hi, Orth: Hi, EntryFree: Div,
  Rdg: Hi, Def: Div, Etym: Hi,
}

const andBibleComponents = {
  AndBibleLink
}

const osisComponents = {
  Verse, W, Div, Chapter, Reference, Note, TransChange,
  DivineName, Seg, Milestone, Title, Q, Hi, CatchWord, List, Item, P,
  Cell, L, Lb, Lg, Row, Table, Foreign, Figure, A, Abbr,
  ...teiComponents, ...andBibleComponents
}

function prefixComponents() {
  const result = {}
  for(const name in osisComponents) {
    result["Osis" + name] = osisComponents[name]
  }
  return result;
}

export default {
  name: "OsisSegment",
  props: {
    osisTemplate: {type: String, required: true},
    convert: {type: Boolean, default: false},
  },
  render() {
    return h({
      template: this.convert ? osisToTemplateString(this.osisTemplate): this.osisTemplate,
      components: {BWA: BibleViewAnchor, ...prefixComponents(osisComponents)},
      compilerOptions: {
        whitespace: 'preserve',
      },
    });
  },
}
</script>

