package net.bible.sharedcore.webdav

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Parses a `207 Multi-Status` body. Only properties inside a `propstat` whose status is 2xx are
 * used (servers report unknown props in a separate 404 propstat). Responses whose href falls
 * outside [DavPath.baseUrl] are dropped.
 */
object MultistatusParser {
    private const val DAV = "DAV:"

    fun parse(xml: String, davPath: DavPath): List<DavResource> {
        val result = mutableListOf<DavResource>()
        try {
            val r = xmlStreaming.newReader(xml)
            var href: String? = null
            var isCollection = false; var length = 0L
            var lastModified: Long? = null; var creation: Long? = null; var etag: String? = null
            // per-propstat scratch, committed only if the propstat status is 2xx
            var psCollection = false; var psLength: Long? = null
            var psLastModified: Long? = null; var psCreation: Long? = null; var psEtag: String? = null
            var psStatusOk = false
            val stack = ArrayDeque<String>()
            val text = StringBuilder()
            while (r.hasNext()) {
                when (r.next()) {
                    EventType.START_ELEMENT -> {
                        val local = if (r.namespaceURI == DAV) r.localName else "?" + r.localName
                        stack.addLast(local); text.clear()
                        when (local) {
                            "response" -> { href = null; isCollection = false; length = 0; lastModified = null; creation = null; etag = null }
                            "propstat" -> { psCollection = false; psLength = null; psLastModified = null; psCreation = null; psEtag = null; psStatusOk = false }
                            "collection" -> if (stack.contains("resourcetype")) psCollection = true
                        }
                    }
                    EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> text.append(r.text)
                    EventType.END_ELEMENT -> {
                        val local = stack.removeLast()
                        val t = text.toString().trim()
                        val inPropstat = stack.contains("propstat")
                        when (local) {
                            "href" -> if (stack.lastOrNull() == "response") href = t
                            "getcontentlength" -> if (inPropstat) psLength = t.toLongOrNull()
                            "getlastmodified" -> if (inPropstat) psLastModified = HttpDates.parseRfc1123(t)
                            "creationdate" -> if (inPropstat) psCreation = HttpDates.parseIso8601(t)
                            "getetag" -> if (inPropstat) psEtag = t.ifEmpty { null }
                            "status" -> if (stack.lastOrNull() == "propstat") psStatusOk = t.split(' ').getOrNull(1)?.startsWith("2") == true
                            "propstat" -> if (psStatusOk) {
                                isCollection = isCollection || psCollection
                                psLength?.let { length = it }
                                psLastModified?.let { lastModified = it }
                                psCreation?.let { creation = it }
                                psEtag?.let { etag = it }
                            }
                            "response" -> {
                                val path = href?.let { davPath.relativize(it) }
                                if (path != null) result += DavResource(path, isCollection, length, lastModified, creation, etag)
                            }
                        }
                        text.clear()
                    }
                    else -> {}
                }
            }
            if (stack.isNotEmpty()) throw IllegalArgumentException("Truncated multistatus")
        } catch (e: XmlException) {
            throw IllegalArgumentException("Malformed multistatus: ${e.message}", e)
        }
        return result
    }
}
