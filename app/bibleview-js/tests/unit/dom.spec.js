/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
import { JSDOM } from "jsdom";
import {
    calculateOffsetToParent,
    calculateOffsetToVerse, contentLength,
    findNext, findNodeAtOffset,
    findParentsBeforeVerseSibling,
    findPreviousSiblingWithClass, lastTextNode, textLength, walkBackText
} from "@/dom";
import {highlightRange} from "@/lib/highlight-range";

const test1 = `
<!DOCTYPE html>
<div>
  <div class="verse" id="o-0">
    text1
    <div id="id1-1">
      text2
      <!-- comment -->
      <b id="id1-1-1"><!--test-->te2.1</b>
      <!-- comment -->
      te2.2
    </div>
    <div id="id1-2" class="skip-offset">
      <!-- comment -->
      note to be ignored
      <b id="id1-3">bold</b>
    </div>
    <div id="id1-4">
      <!-- comment -->
      text3
      <b id="id1-5" class="skip-offset">to be ignored</b>
      text4
    </div>    
    text5
    <b class="skip-offset">skipped</b>
  </div>
  <div id="between-1" class="skip-offset">Outside of <!-- test -->verse</div>
  <div id="between-2">legal</div>
  <div id="between-3" class="skip-offset">Outside of verse
    test
    <!-- test -->
    <b class="skip-offset" id="b-3-1">test</b>
    <b id="b-3-2">test1</b>
    test
  </div>
  <div class="verse" id="o-1">
    text6
    <div id="id2-1">
      text7
    </div>
    <div id="id2-2" class="skip-offset">
      note to be ignored
      <b id="id2-3">bold</b>
    </div>
    <div id="id2-4">
      text8
      <b id="id2-5" class="skip-offset">to be ignored</b>
      text9
    </div>
    tex10
  </div>  
</div>`



function getDom(html) {
    let stripped = html.replace(/ *\n */g, "");
    return new JSDOM(stripped);
}

describe("highlighting", () => {
   it("test that highlight and undoing work", () => {
       const hlTestDoc = `
<!DOCTYPE html>
<div>
  <div class="verse" id="o-0">
    text1
  </div>
</div>  
`;
       const highLighted = `<html><head></head><body><div><div class="verse" id="o-0"><span style="width:1">text1</span></div></div></body></html>`
       const unHighLighted = `<html><head></head><body><div><div class="verse" id="o-0">text1</div></div></body></html>`
       const dom = getDom(hlTestDoc);
       const document = dom.window.document;
       const range = document.createRange();
       const e = document.querySelector("#o-0")
       {
           const node1 = e.childNodes[0];
           range.setStart(node1, 0);
           range.setEnd(node1, 5);
           expect(e.childNodes.length).toBe(1);
           const {undo} = highlightRange(range, "span", {style: "width:1"});
           expect(e.childNodes.length).toBe(1);
           expect(document.documentElement.outerHTML).toBe(highLighted);
           undo();
           expect(e.childNodes.length).toBe(1);
           expect(document.documentElement.outerHTML).toBe(unHighLighted);
       }
       {
           const node1 = e.childNodes[0];
           range.setStart(node1, 0);
           range.setEnd(node1, 5);
           const {undo} = highlightRange(range, "span", {style: "width:1"});
           expect(document.documentElement.outerHTML).toBe(highLighted);
           undo();
           expect(document.documentElement.outerHTML).toBe(unHighLighted);
       }
   });

    it("test that highlight two consequent highlight ranges work", () => {
        const hlTestDoc = `
<!DOCTYPE html>
<div>
  <div class="verse" id="o-0">
    text1text2
  </div>
</div>  
`;
        const highLighted = `<html><head></head><body><div><div class="verse" id="o-0"><span style="width:1">text1</span><span style="width:2">text2</span></div></div></body></html>`
        const highLighted1 = `<html><head></head><body><div><div class="verse" id="o-0"><span style="width:1">text1</span>text2</div></div></body></html>`
        const unHighLighted1 = `<html><head></head><body><div><div class="verse" id="o-0">text1<span style="width:2">text2</span></div></div></body></html>`
        const unHighLighted = `<html><head></head><body><div><div class="verse" id="o-0">text1text2</div></div></body></html>`
        const dom = getDom(hlTestDoc);
        const document = dom.window.document;
        const range = document.createRange();
        const range2 = document.createRange();
        const e = document.querySelector("#o-0")
        {
            const node1 = e.childNodes[0];
            range.setStart(node1, 0);
            range.setEnd(node1, 5);
            range2.setStart(node1, 5);
            range2.setEnd(node1, 10);
            expect(e.childNodes.length).toBe(1);

            const {undo: undo1} = highlightRange(range, "span", {style: "width:1"});
            expect(document.documentElement.outerHTML).toBe(highLighted1);
            expect(e.childNodes.length).toBe(2);
            const {undo: undo2} = highlightRange(range2, "span", {style: "width:2"});
            expect(e.childNodes.length).toBe(2);
            expect(document.documentElement.outerHTML).toBe(highLighted);
            undo1();
            expect(document.documentElement.outerHTML).toBe(unHighLighted1);
            expect(e.childNodes.length).toBe(3);
            undo2();
            expect(e.childNodes.length).toBe(3);
            expect(document.documentElement.outerHTML).toBe(unHighLighted);
        }
        for(let i=0; i<5; i++) {
            const node1 = e.childNodes[0];
            const node2 = e.childNodes[2];
            range.setStart(node1, 0);
            range.setEnd(node1, 5);
            range2.setStart(node2, 0);
            range2.setEnd(node2, 5);
            expect(e.childNodes.length).toBe(3);

            const {undo: undo1} = highlightRange(range, "span", {style: "width:1"});
            expect(document.documentElement.outerHTML).toBe(highLighted1);
            expect(e.childNodes.length).toBe(3);
            const {undo: undo2} = highlightRange(range2, "span", {style: "width:2"});
            expect(e.childNodes.length).toBe(3);
            expect(document.documentElement.outerHTML).toBe(highLighted);
            undo1();
            expect(document.documentElement.outerHTML).toBe(unHighLighted1);
            expect(e.childNodes.length).toBe(3);
            undo2();
            expect(e.childNodes.length).toBe(3);
            expect(document.documentElement.outerHTML).toBe(unHighLighted);
        }

    });

    it("test 2 (undo's in opposite order) that highlight two consequent highlight ranges work", () => {
        const hlTestDoc = `
<!DOCTYPE html>
<div>
  <div class="verse" id="o-0">
    text1text2
  </div>
</div>  
`;
        const highLighted = `<html><head></head><body><div><div class="verse" id="o-0"><span style="width:1">text1</span><span style="width:1">text2</span></div></div></body></html>`
        const unHighLighted = `<html><head></head><body><div><div class="verse" id="o-0">text1text2</div></div></body></html>`
        const dom = getDom(hlTestDoc);
        const document = dom.window.document;
        const range = document.createRange();
        const range2 = document.createRange();
        const e = document.querySelector("#o-0")
        {
            const node1 = e.childNodes[0];
            range.setStart(node1, 0);
            range.setEnd(node1, 5);
            range2.setStart(node1, 5);
            range2.setEnd(node1, 10);
            expect(e.childNodes.length).toBe(1);

            const {undo: undo1} = highlightRange(range, "span", {style: "width:1"});
            expect(e.childNodes.length).toBe(2);
            const {undo: undo2} = highlightRange(range2, "span", {style: "width:1"});
            expect(e.childNodes.length).toBe(2);
            expect(document.documentElement.outerHTML).toBe(highLighted);
            undo2();
            undo1();
            expect(e.childNodes.length).toBe(3);
            expect(document.documentElement.outerHTML).toBe(unHighLighted);
        }
    });
});

describe("textLength tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#o-0")
        const length = textLength(e);
        expect(length).toBe(35);
    });
});

describe("lastTextNode tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#o-0")
        const node = lastTextNode(e);
        console.log("node", node);
        expect(node.textContent).toBe("text5");
    });
});



describe("findPreviousSiblingsWithClass tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#between-2").firstChild
        const {siblings, verseNode} = findPreviousSiblingWithClass(e, "verse")
        expect(siblings.length).toBe(2);
        expect(verseNode.id).toBe("o-0");
    });
    it("test2", () => {
        const e = document.querySelector("#between-1").firstChild
        const {siblings, verseNode} = findPreviousSiblingWithClass(e, "verse")
        expect(siblings.length).toBe(1);
        expect(verseNode.id).toBe("o-0");
    });
    it("test3", () => {
        const e = document.querySelector("#b-3-1").firstChild
        const {siblings, verseNode} = findPreviousSiblingWithClass(e, "verse")
        expect(siblings.length).toBe(1);
        expect(verseNode).toBe(null);
    });
    it("test4", () => {
        const e = document.querySelector("#between-3").firstChild
        const {siblings, verseNode} = findPreviousSiblingWithClass(e, "verse")
        expect(siblings.length).toBe(3);
        expect(verseNode.id).toBe("o-0");
    });
});

describe("findParentsBeforeVerseSiblings tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#between-2").firstChild
        const {parent, siblings, verseNode} = findParentsBeforeVerseSibling(e)
        expect(parent.id).toBe("between-2");
        expect(siblings.length).toBe(2);
        expect(verseNode.id).toBe("o-0");
    });

    it("test2", () => {
        const e = document.querySelector("#between-2")
        const {parent, siblings, verseNode} = findParentsBeforeVerseSibling(e)
        expect(parent.id).toBe("between-2");
        expect(siblings.length).toBe(2);
        expect(verseNode.id).toBe("o-0");
    });
});


describe("walkBack tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#id1-1").firstChild
        const vals = Array.from(walkBackText(e)).map(v => v.textContent);
        expect(vals).toEqual(["text2", "text1"]);
    })

    it("test2", () => {
        const e = document.querySelector("#id1-1-1").firstChild
        const vals = Array.from(walkBackText(e)).map(v => v.textContent);
        expect(vals).toEqual(["text2", "text1"]);
    })

    it("test3", () => {
        const e = document.querySelector("#id1-1-1").lastChild
        const vals = Array.from(walkBackText(e)).map(v => v.textContent);
        expect(vals).toEqual(["te2.1", "text2", "text1"]);
    })

    it("test4", () => {
        const e = document.querySelector("#id1-1-1")
        const vals = Array.from(walkBackText(e)).map(v => v.textContent);
        expect(vals).toEqual(["te2.1", "text2", "text1"]);
    })

    it("test5", () => {
        const e = document.querySelector("#id1-2")
        const vals = Array.from(walkBackText(e)).map(v => v.textContent);
        expect(vals).toEqual(["bold", "note to be ignored", "te2.2", "te2.1", "text2", "text1"]);
    })

    it("test6", () => {
        const e = document.querySelector("#id1-2")
        const vals = Array.from(walkBackText(e, true)).map(v => v.textContent);
        expect(vals).toEqual(["te2.2", "te2.1", "text2", "text1"]);
    })
});

describe("findNext tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test1", () => {
        const e = document.querySelector("#id1-1").firstChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text1");
    })
    it("test1.1", () => {
        const e = document.querySelector("#id1-1").lastChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("te2.1");
    })
    it("test1.2", () => {
        const e = document.querySelector("#id1-1-1").lastChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text2");
    })

    it("test2", () => {
        const e = document.querySelector("#id1-1")
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        // NOTE: element nodes will be started from their end.
        expect(next.textContent).toBe("te2.2");
    })
    it("test2.1", () => {
        const e = document.querySelector("#id1-1")
        const next = findNext(e, e)
        expect(next.nodeType).toBe(3)
        // NOTE: element nodes will be started from their end.
        expect(next.textContent).toBe("te2.2");
    })
    it("test2.2", () => {
        const e = document.querySelector("#id1-1").lastChild
        const next = findNext(e, e.parentNode)
        expect(next.nodeType).toBe(3)
        // NOTE: element nodes will be started from their end.
        expect(next.textContent).toBe("te2.1");
    })

    it("test3", () => {
        const e = document.querySelector("#id1-5").nextSibling
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("to be ignored");
    })
    it("test3.1", () => {
        const e = document.querySelector("#id1-5").nextSibling
        const next = findNext(e, null, true)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text3");
    })

    it("test4", () => {
        const e = document.querySelector("#between-1").firstChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("skipped");
    })

    it("test4.1", () => {
        const e = document.querySelector("#between-1").firstChild
        const next = findNext(e, null, true)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text5");
    })

    it("test5", () => {
        const e = document.querySelector("#between-2").firstChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("verse");
    })
    it("test5.1", () => {
        const e = document.querySelector("#between-2").firstChild
        const next = findNext(e, null, true)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text5");
    })
    it("test6", () => {
        const e = document.querySelector("#id1-4").firstChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("bold");
    })
    it("test7", () => {
        const e = document.querySelector("#id1-5").firstChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text3");
    })
    it("test8", () => {
        const e = document.querySelector("#id1-4").lastChild
        const next = findNext(e, null)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("to be ignored");
    })
    it("test8.1", () => {
        const e = document.querySelector("#id1-4").lastChild
        const next = findNext(e, null, true)
        expect(next.nodeType).toBe(3)
        expect(next.textContent).toBe("text3");
    })
    it("test9", () => {
        const e = document.querySelector("#between-1")
        const next = findNext(e, e, true)
        expect(next).toBe(null);
    })

    it("test10", () => {
        const e = document.querySelector("#between-2").firstChild
        const next = findNext(e, e.parentNode, true)
        expect(next).toBe(null)
    })
});

describe("calculateOffsetToVerse tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("findLegalPosition test 1", () => {
        const elem1 = document.querySelector("#id1-1").firstChild

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 0)
        expect(ordinal).toBe(0)
        expect(offset).toBe(5)
    });
    it("findLegalPosition elem", () => {
        const elem1 = document.querySelector("#id1-1")

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 0)
        expect(ordinal).toBe(0)
        expect(offset).toBe(5)
    });
    it("findLegalPosition test 2 start from comment", () => {
        const elem1 = document.querySelector("#id1-2").firstChild

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 8)
        expect(ordinal).toBe(0)
        expect(offset).toBe(4*5)
    });
    it("findLegalPosition test 2.1 start from skip-offset", () => {
        const sel = document.querySelector("#id1-2");
        const elem1 = sel.childNodes[1]

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 8)
        expect(ordinal).toBe(0)
        expect(offset).toBe(4*5)
    });
    it("gives offset relative to next/previous verse if outside of verse", () => {
        const elem1 = document.querySelector("#between-1").firstChild

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 2)
        expect(ordinal).toBe(0)
        expect(offset).toBe(7*5)
    });
    it("test calculateOffsetToParent", () => {
        const elem1 = document.querySelector("#between-2").firstChild

        const offset = calculateOffsetToParent(elem1, elem1.parentNode, 2)
        expect(offset).toBe(2)
    });
    it("test calculateOffsetToParent test 2", () => {
        const elem1 = document.querySelector("#between-2").firstChild

        const offset = calculateOffsetToParent(elem1, elem1.parentNode, elem1.length)
        expect(offset).toBe(5)
    });
    it("test calculateOffsetToParent test 3", () => {
        const elem1 = document.querySelector("#between-2")
        const t = findNext(elem1, null, true);
        const offset = calculateOffsetToParent(t, elem1, t.length)
        expect(offset).toBe(5)
    });
    it("test calculateOffsetToParent test 4", () => {
        const elem1 = document.querySelector("#o-0")
        const t = findNext(elem1, elem1, true);

        const offset = calculateOffsetToParent(t, elem1, t.length)
        expect(offset).toBe(7*5)
    });
    it("gives offset relative to next/previous verse if outside of verse, test 2", () => {
        const elem1 = document.querySelector("#between-3").firstChild

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 2)
        expect(ordinal).toBe(0)
        expect(offset).toBe(8*5)
    });
    it("gives offset relative to next/previous verse if outside of verse, test 3", () => {
        const elem1 = document.querySelector("#between-2").firstChild

        const {ordinal, offset} = calculateOffsetToVerse(elem1, 2)
        expect(ordinal).toBe(0)
        expect(offset).toBe(7*5 + 2)
    });
});

describe("contentLength tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    it("test 1", () => {
        const elem1 = document.querySelector("#id1-1-1")
        expect(contentLength(elem1)).toBe(5)
    })


    it("test 2", () => {
        const elem1 = document.querySelector("#id1-1")
        expect(contentLength(elem1)).toBe(15)
    })

    it("test 3", () => {
        const elem1 = document.querySelector("#id1-4")
        expect(contentLength(elem1)).toBe(10)
    })
});

describe("findNodeAtOffset tests", () => {
    let dom, document;
    beforeEach(() => {
        dom = getDom(test1);
        document = dom.window.document;
    })

    function testOffset(testOffset, resultText, resultOffset) {
        const elem1 = document.querySelector("#o-0")

        const result = findNodeAtOffset(elem1, testOffset, resultText, resultOffset)
        const [node, offset] = result;
        expect(node.textContent).toBe(resultText)
        expect(offset).toBe(resultOffset)
    }

    it("findNodeAtOffset test 1",
        () => testOffset(0, "text1", 0));

    it("findNodeAtOffset test 2",
        () => testOffset(5, "text1", 5));

    it("findNodeAtOffset test 3",
        () => testOffset(6, "text2", 1));

    it("findNodeAtOffset test 4",
        () => testOffset(10, "text2", 5));

    it("findNodeAtOffset test 5",
        () => testOffset(11, "te2.1", 1));

    it("findNodeAtOffset test 6",
        () => testOffset(15, "te2.1", 5));

    it("findNodeAtOffset test 7",
        () => testOffset(16, "te2.2", 1));

    it("findNodeAtOffset test 8",
        () => testOffset(21, "text3", 1));

    it("findNodeAtOffset test 9",
        () => testOffset(26, "text4", 1));

    it("findNodeAtOffset test 10",
        () => testOffset(31, "text5", 1));

    it("findNodeAtOffset test 11",
        () => testOffset(36, "legal", 1));

    it("findNodeAtOffset test 12",
        () => testOffset(41, "text6", 1));

    it("findNodeAtOffset test 13",
        () => testOffset(46, "text7", 1));

});
