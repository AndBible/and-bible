/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.nextcloud

import com.owncloud.android.lib.common.OwnCloudClient
import org.apache.jackrabbit.webdav.client.methods.SearchMethod
import org.apache.jackrabbit.webdav.search.SearchInfo
import org.apache.jackrabbit.webdav.xml.Namespace
import org.w3c.dom.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class NextCloudSearchMethod(
    client: OwnCloudClient,
    val folder: String,
    val lastModifiedAtLeast: Long,
    private val fileName: String? = null,
    private val resultLimit: Int? = null,
) : SearchMethod(
    client.davUri.toString(),
    SearchInfo(
        "NC",
        Namespace.XMLNS_NAMESPACE,
        "/"
    )

    ) {
    private var listingDocument: Document? = null

    // Jackrabbit parses the body during executeMethod but does not retain the original XML.
    override fun getResponseBodyAsDocument(): Document? = listingDocument
        ?: super.getResponseBodyAsDocument()?.also { listingDocument = it }

    val userId = client.userIdPlain
    init {
        setRequestHeader(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_VALUE)
        setRequestBody(createQuery())
    }

    private fun createQuery(): Document? {
        val lastModifiedAtLeastDate: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(lastModifiedAtLeast))
        val template = """
        <d:searchrequest xmlns:d="$DAV_NAMESPACE" xmlns:oc="http://owncloud.org/ns">
            <d:basicsearch>
                <d:select>
                    <d:prop>
                      <oc:fileid/>
                      <d:displayname/>
                      <oc:size/>
                      <d:getcontenttype/>
                      <d:getlastmodified/>
                    </d:prop>
                </d:select>
                <d:from>
                    <d:scope>
                        <d:href/>
                        <d:depth>infinity</d:depth>
                    </d:scope>
                </d:from>
                <d:where>
                    <d:gt>
                        <d:prop>
                            <d:getlastmodified/>
                        </d:prop>
                        <d:literal>$lastModifiedAtLeastDate</d:literal>
                    </d:gt>                
                </d:where>                                
                <d:orderby>
                    <d:order>
                        <d:prop>
                            <d:getlastmodified/>
                        </d:prop>
                        <d:ascending/>
                    </d:order>
                </d:orderby>
            </d:basicsearch>
        </d:searchrequest> 
        """.trimIndent()

        return try {
            val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            builder.parse(template.byteInputStream()).apply {
                // Account and folder names are XML text, never markup in the query template.
                getElementsByTagNameNS(DAV_NAMESPACE, "href").item(0).textContent = "/files/$userId/$folder"
                fileName?.let { name ->
                    val where = getElementsByTagNameNS(DAV_NAMESPACE, "where").item(0)
                    val gt = getElementsByTagNameNS(DAV_NAMESPACE, "gt").item(0)
                    val and = createElementNS(DAV_NAMESPACE, "d:and")
                    where.replaceChild(and, gt)
                    and.appendChild(gt)
                    val eq = createElementNS(DAV_NAMESPACE, "d:eq")
                    val prop = createElementNS(DAV_NAMESPACE, "d:prop")
                    prop.appendChild(createElementNS(DAV_NAMESPACE, "d:displayname"))
                    eq.appendChild(prop)
                    eq.appendChild(createElementNS(DAV_NAMESPACE, "d:literal").apply { textContent = name })
                    and.appendChild(eq)
                }
                resultLimit?.let { limit ->
                    // Nextcloud versions may silently default an omitted limit to 100 results.
                    val element = createElementNS(DAV_NAMESPACE, "d:limit")
                    element.appendChild(createElementNS(DAV_NAMESPACE, "d:nresults").apply { textContent = limit.toString() })
                    getElementsByTagNameNS(DAV_NAMESPACE, "basicsearch").item(0).appendChild(element)
                }
            }
        } catch (parserError: ParserConfigurationException) {
            System.err.println("ParserConfigurationException: " + parserError.getLocalizedMessage())
            return null
        }
    }

    companion object {
        private const val HEADER_CONTENT_TYPE_VALUE = "text/xml"
        private const val DAV_NAMESPACE = "DAV:"
    }
}
