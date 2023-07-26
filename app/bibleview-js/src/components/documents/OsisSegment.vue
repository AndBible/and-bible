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

<script lang="ts">

import Verse from "@/components/OSIS/Verse.vue";
import W from "@/components/OSIS/W.vue";
import Div from "@/components/OSIS/Div.vue";
import Chapter from "@/components/OSIS/Chapter.vue";
import Reference from "@/components/OSIS/Reference.vue";
import Note from "@/components/OSIS/Note.vue";
import TransChange from "@/components/OSIS/TransChange.vue";
import DivineName from "@/components/OSIS/DivineName.vue";
import Seg from "@/components/OSIS/Seg.vue";
import Milestone from "@/components/OSIS/Milestone.vue";
import Title from "@/components/OSIS/Title.vue";
import Q from "@/components/OSIS/Q.vue";
import Hi from "@/components/OSIS/Hi.vue";
import CatchWord from "@/components/OSIS/CatchWord.vue";
import List from "@/components/OSIS/List.vue";
import Item from "@/components/OSIS/Item.vue";
import P from "@/components/OSIS/P.vue";
import Cell from "@/components/OSIS/Cell.vue";
import L from "@/components/OSIS/L.vue";
import Lb from "@/components/OSIS/Lb.vue";
import Lg from "@/components/OSIS/Lg.vue";
import Row from "@/components/OSIS/Row.vue";
import Table from "@/components/OSIS/Table.vue";
import Foreign from "@/components/OSIS/Foreign.vue";
import Figure from "@/components/OSIS/Figure.vue";
import A from "@/components/OSIS/A.vue";
import Abbr from "@/components/OSIS/Abbr.vue";
import BibleViewAnchor from "@/components/BibleViewAnchor.vue";
import AndBibleLink from "@/components/OSIS/AndBibleLink.vue";
import Pb from "@/components/MyBible/Pb.vue";
import NoOp from "@/components/OSIS/NoOp.vue";
import H3 from "@/components/MyBible/H3.vue";
import I from "@/components/MyBible/I.vue";
import S from "@/components/MyBible/S.vue";
import B from "@/components/MyBible/B.vue";
import Br from "@/components/MyBible/Br.vue";
import Li from "@/components/MyBible/Li.vue";
import Ol from "@/components/MyBible/Ol.vue";
import Strong from "@/components/MyBible/Strong.vue";

import {Component, defineComponent, h} from "vue";
import {osisToTemplateString} from "@/utils";

const teiComponents = {
    Ref: Reference, Pron: Hi, Orth: Hi, EntryFree: Div,
    Rdg: Hi, Def: Div, Etym: Hi,
}

const andBibleComponents = {
    AndBibleLink
}

const myBibleComponents = {
    S, M: NoOp, I, J: Q, N: Note, Pb, F: NoOp, H: Title, E: Hi, H3, B, Br, Li, Ol, Strong
}

const osisComponents = {
    Verse, W, Div, Chapter, Reference, Note, TransChange,
    DivineName, Seg, Milestone, Title, Q, Hi, CatchWord, List, Item, P,
    Cell, L, Lb, Lg, Row, Table, Foreign, Figure, A, Abbr, BWA: BibleViewAnchor,
    ...teiComponents, ...andBibleComponents, ...myBibleComponents,
}

function prefixComponents(components: Record<string, Component>): Record<string, Component> {
    const result: Record<string, Component> = {}
    for (const name in components) {
        result["Osis" + name] = components[name]
    }
    return result;
}

export default defineComponent({
    name: "OsisSegment",
    props: {
        osisTemplate: {type: String, required: true},
        convert: {type: Boolean, default: false},
    },
    render() {
        return h({
            template: this.convert ? osisToTemplateString(this.osisTemplate) : this.osisTemplate,
            components: prefixComponents(osisComponents),
            compilerOptions: {
                whitespace: 'preserve',
            },
        });
    },
})
</script>

