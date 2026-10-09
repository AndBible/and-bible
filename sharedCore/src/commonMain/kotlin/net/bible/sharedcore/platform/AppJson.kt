package net.bible.sharedcore.platform

import kotlinx.serialization.json.Json

/** The app-wide Json configuration (was `CommonUtils.json`). */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
