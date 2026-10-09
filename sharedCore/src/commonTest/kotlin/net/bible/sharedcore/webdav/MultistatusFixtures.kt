package net.bible.sharedcore.webdav

/** Depth:1 PROPFIND responses in the shape each server family produces (base path in comment). */
object MultistatusFixtures {
    // Apache mod_dav, base https://nas.local/dav/ ; prefix lp1/lp2, D: prefix, full hrefs absent
    val APACHE = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:" xmlns:ns0="DAV:">
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/sync/</D:href>
        <D:propstat><D:prop>
        <lp1:resourcetype><D:collection/></lp1:resourcetype>
        <lp1:creationdate>2026-10-01T10:00:00Z</lp1:creationdate>
        <lp1:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</lp1:getlastmodified>
        <lp1:getetag>"1000-5a"</lp1:getetag>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/sync/device%20one/</D:href>
        <D:propstat><D:prop>
        <lp1:resourcetype><D:collection/></lp1:resourcetype>
        <lp1:getlastmodified>Thu, 01 Oct 2026 10:04:00 GMT</lp1:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
        </D:response>
        <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
        <D:href>/dav/sync/1.12.sqlite3.gz</D:href>
        <D:propstat><D:prop>
        <lp1:resourcetype/>
        <lp1:getcontentlength>2048</lp1:getcontentlength>
        <lp1:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</lp1:getlastmodified>
        <lp1:getetag>"800-5b"</lp1:getetag>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
        </D:response>
        </D:multistatus>
    """.trimIndent()

    // nginx + nginx-dav-ext-module, base https://nas.local/dav/ ; D: prefix, no creationdate/etag
    val NGINX = """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:multistatus xmlns:D="DAV:">
        <D:response><D:href>/dav/sync/</D:href><D:propstat><D:prop>
        <D:resourcetype><D:collection/></D:resourcetype>
        <D:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</D:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        <D:response><D:href>/dav/sync/device%20one/</D:href><D:propstat><D:prop>
        <D:resourcetype><D:collection/></D:resourcetype>
        <D:getlastmodified>Thu, 01 Oct 2026 10:04:00 GMT</D:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        <D:response><D:href>/dav/sync/1.12.sqlite3.gz</D:href><D:propstat><D:prop>
        <D:resourcetype/>
        <D:getcontentlength>2048</D:getcontentlength>
        <D:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</D:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        </D:multistatus>
    """.trimIndent()

    // rclone serve webdav, base https://nas.local/dav/ ; lowercase d:, unknown props in a 404 propstat
    val RCLONE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <D:multistatus xmlns:D="DAV:">
        <D:response><D:href>/dav/sync/</D:href><D:propstat><D:prop>
        <D:resourcetype><D:collection xmlns:D="DAV:"/></D:resourcetype>
        <D:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</D:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
        <D:propstat><D:prop><D:creationdate></D:creationdate><D:getetag></D:getetag></D:prop>
        <D:status>HTTP/1.1 404 Not Found</D:status></D:propstat></D:response>
        <D:response><D:href>/dav/sync/device%20one/</D:href><D:propstat><D:prop>
        <D:resourcetype><D:collection xmlns:D="DAV:"/></D:resourcetype>
        <D:getlastmodified>Thu, 01 Oct 2026 10:04:00 GMT</D:getlastmodified>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        <D:response><D:href>/dav/sync/1.12.sqlite3.gz</D:href><D:propstat><D:prop>
        <D:resourcetype></D:resourcetype>
        <D:getcontentlength>2048</D:getcontentlength>
        <D:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</D:getlastmodified>
        <D:getetag>"18a2b"</D:getetag>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        </D:multistatus>
    """.trimIndent()

    // Nextcloud, base https://cloud.example.com/remote.php/dav/files/me/ ; d: prefix, oc/nc namespaces
    val NEXTCLOUD = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
        <d:response><d:href>/remote.php/dav/files/me/sync/</d:href><d:propstat><d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
        <d:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</d:getlastmodified>
        <d:getetag>&quot;6703a1b2c3&quot;</d:getetag>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
        <d:propstat><d:prop><d:getcontentlength/><d:creationdate/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>
        <d:response><d:href>/remote.php/dav/files/me/sync/device%20one/</d:href><d:propstat><d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
        <d:getlastmodified>Thu, 01 Oct 2026 10:04:00 GMT</d:getlastmodified>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        <d:response><d:href>/remote.php/dav/files/me/sync/1.12.sqlite3.gz</d:href><d:propstat><d:prop>
        <d:resourcetype/>
        <d:getcontentlength>2048</d:getcontentlength>
        <d:getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</d:getlastmodified>
        <d:getetag>&quot;abc&quot;</d:getetag>
        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
    """.trimIndent()

    // Synology WebDAV Server (Apache-based), base https://nas.local:5006/ ; default namespace, full-URL hrefs
    val SYNOLOGY = """
        <?xml version="1.0" encoding="utf-8"?>
        <multistatus xmlns="DAV:">
        <response><href>https://nas.local:5006/sync/</href><propstat><prop>
        <resourcetype><collection/></resourcetype>
        <getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</getlastmodified>
        <creationdate>2026-10-01T10:00:00Z</creationdate>
        </prop><status>HTTP/1.1 200 OK</status></propstat></response>
        <response><href>https://nas.local:5006/sync/device%20one/</href><propstat><prop>
        <resourcetype><collection/></resourcetype>
        <getlastmodified>Thu, 01 Oct 2026 10:04:00 GMT</getlastmodified>
        </prop><status>HTTP/1.1 200 OK</status></propstat></response>
        <response><href>https://nas.local:5006/sync/1.12.sqlite3.gz</href><propstat><prop>
        <resourcetype/>
        <getcontentlength>2048</getcontentlength>
        <getlastmodified>Thu, 01 Oct 2026 10:05:00 GMT</getlastmodified>
        </prop><status>HTTP/1.1 200 OK</status></propstat></response>
        </multistatus>
    """.trimIndent()
}
