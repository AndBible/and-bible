package net.bible.sharedui.desktop.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class DesktopRelease(val tag: String, val build: Int, val production: Boolean, val htmlUrl: String)

fun parseDesktopReleases(json: String): List<DesktopRelease> {
    val releases = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
    val tagPattern = Regex("^(test|production)-(\\d+)$")
    return releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release["draft"]?.jsonPrimitive?.booleanOrNull != false) return@mapNotNull null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val match = tagPattern.matchEntire(tag) ?: return@mapNotNull null
        val build = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val assets = release["assets"] as? JsonArray ?: return@mapNotNull null
        if (assets.none { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.startsWith("andbible-desktop-") == true }) return@mapNotNull null
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        DesktopRelease(tag, build, match.groupValues[1] == "production", url)
    }
}

fun newestOffered(releases: List<DesktopRelease>, ownBuild: Int, ownIsProduction: Boolean, announcedBuild: Int): DesktopRelease? =
    releases.filter { it.build > ownBuild && it.build > announcedBuild && (!ownIsProduction || it.production) }
        .maxByOrNull { it.build }
