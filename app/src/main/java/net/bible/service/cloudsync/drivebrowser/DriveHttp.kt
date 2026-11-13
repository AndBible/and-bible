package net.bible.service.cloudsync.drivebrowser

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient

/** The process-wide Drive HTTP client (one OkHttp engine), shared by the Play and the browser sign-in providers. */
object DriveHttp {
    val sharedClient: HttpClient by lazy { createGoogleDriveHttpClient(OkHttp.create()) }
}
