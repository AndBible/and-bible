package net.bible.service.cloudsync

/**
 * A sync failure the user must see and act on (wrong credentials, changed certificate, full
 * storage). Deliberately not an IOException, which the sync layer would retry silently.
 * [requiresReconnect]: the adapter must be dropped until the user signs in again.
 */
class CloudSyncUserFacingException(message: String, val requiresReconnect: Boolean, cause: Throwable? = null) :
    Exception(message, cause)
