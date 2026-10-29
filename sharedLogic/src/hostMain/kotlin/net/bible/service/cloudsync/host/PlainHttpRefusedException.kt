package net.bible.service.cloudsync.host

import okio.IOException

/** A stored Nextcloud URL is no longer permitted by the platform's local-HTTP policy. */
class PlainHttpRefusedException(val url: String) : IOException("Plain HTTP refused by platform policy")
