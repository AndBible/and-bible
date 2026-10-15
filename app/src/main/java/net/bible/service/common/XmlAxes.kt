package net.bible.service.common

import org.jdom2.Content
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.Parent
import org.jdom2.Text

/** Absolute queries have no root on detached trees, matching jaxen. Mirrors kdom.query.Axes. */
public fun Parent.xpathRoot(): Document? = this as? Document ?: document

/** Document-order elements, including the document's root. */
public fun Document.allElements(): Sequence<Element> = descendantElements()

public fun Parent.descendantElements(): Sequence<Element> = descendantContent().filterIsInstance<Element>()
public fun Parent.descendantTexts(): Sequence<Text> = descendantContent().filterIsInstance<Text>()
public fun Element.ancestorElements(): Sequence<Element> = generateSequence(parentElement) { it.parentElement }
public fun Element.childElements(): Sequence<Element> = contentSequence().filterIsInstance<Element>()

public fun Element.followingSiblingElements(): Sequence<Element> = sequence {
    val p = parent ?: return@sequence
    var i = p.indexOf(this@followingSiblingElements) + 1
    while (i < p.contentSize) {
        val c = p.getContent(i++)
        if (c is Element) yield(c)
    }
}

/** Reverse axis: nearest sibling first. */
public fun Element.precedingSiblingElements(): Sequence<Element> = sequence {
    val p = parent ?: return@sequence
    var i = p.indexOf(this@precedingSiblingElements) - 1
    while (i >= 0) {
        val c = p.getContent(i--)
        if (c is Element) yield(c)
    }
}

public fun Element.isNamed(localName: String, ns: Namespace): Boolean =
    name == localName && namespaceURI == ns.uri
public fun Element.hasAttribute(name: String): Boolean = getAttribute(name) != null
public fun Element.attributeIs(name: String, value: String): Boolean = getAttribute(name)?.value == value

public fun Parent.findAll(localName: String, ns: Namespace): List<Element> =
    xpathRoot()?.allElements()?.filter { it.isNamed(localName, ns) }?.toList() ?: emptyList()

/** Filter children in document order, rather than grouping by potentially nested parents. */
public fun Parent.findChildrenOf(
    parentName: String, parentNs: Namespace, childName: String, childNs: Namespace,
    predicate: (Element) -> Boolean = { true },
): List<Element> =
    xpathRoot()?.allElements()?.filter {
        it.isNamed(childName, childNs) && it.parentElement?.isNamed(parentName, parentNs) == true && predicate(it)
    }?.toList() ?: emptyList()

private fun Parent.contentSequence(): Sequence<Content> = sequence {
    var i = 0
    while (i < contentSize) yield(getContent(i++))
}

/** Iterative pre-order walk; sequences are live, so mutation callers must first snapshot with toList(). */
private fun Parent.descendantContent(): Sequence<Content> = sequence {
    val parents = ArrayList<Parent>()
    val indexes = ArrayList<Int>()
    parents.add(this@descendantContent)
    indexes.add(0)
    while (parents.isNotEmpty()) {
        val top = parents.size - 1
        val p = parents[top]
        val i = indexes[top]
        if (i >= p.contentSize) {
            parents.removeAt(top)
            indexes.removeAt(top)
            continue
        }
        indexes[top] = i + 1
        val c = p.getContent(i)
        yield(c)
        if (c is Element) {
            parents.add(c)
            indexes.add(0)
        }
    }
}
