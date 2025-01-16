package net.bible.service.cloudsync.nextcloud

import org.apache.jackrabbit.webdav.client.methods.SearchMethod
import org.apache.jackrabbit.webdav.search.SearchInfo
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class NextCloudSearchMethod(
    uri: String?,
    val folder: String,
    val lastModifiedAtLeast: Long,
    searchInfo: SearchInfo
) : SearchMethod(uri, searchInfo) {

    init {
        setRequestHeader(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_VALUE)
        setRequestBody(createQuery())
    }

    private fun createQuery(): Document? {
        val template = """
                       <?xml version="1.0" encoding="UTF-8"?>
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
                                       <d:href>$folder</d:href>
                                       <d:depth>infinity</d:depth>
                                   </d:scope>
                               </d:from>
                               <d:where>
                                   <d:gt>
                                       <d:prop>
                                           <d:getlastmodified/>
                                       </d:prop>
                                       <d:literal>$lastModifiedAtLeast</d:literal>
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
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            builder.parse(template)
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
