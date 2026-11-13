package net.bible.service.cloudsync.drive

import org.koin.core.qualifier.named

/** Koin qualifier of the Drive-specific `HttpClient` (timeouts, `expectSuccess = false`); never bind it unqualified. */
val GOOGLE_DRIVE_HTTP = named("googleDrive")
