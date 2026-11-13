package net.bible.service.cloudsync.drive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.bible.sharedcore.platform.cloud.CloudFile
import okio.IOException
import kotlin.time.Instant

/** Drive v3 `File` resource, restricted to the fields requested via [DRIVE_FILE_FIELDS]. */
@Serializable
internal class DriveFileDto(
    val id: String,
    val name: String = "",
    /** Drive sends sizes as decimal strings, and omits them for folders. */
    val size: String? = null,
    val createdTime: String? = null,
    val parents: List<String> = emptyList(),
    val mimeType: String? = null,
)

/** Drive v3 `files.list` response page. */
@Serializable
internal class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null,
)

/** Metadata sent when creating a file or folder, or starting a resumable upload. */
@Serializable
internal class DriveCreateDto(
    val name: String,
    val parents: List<String>,
    val mimeType: String? = null,
)

/** Google API error envelope: `{"error": {"code": 404, "message": "...", "errors": [{"reason": "notFound"}]}}`. */
@Serializable
internal class DriveErrorEnvelope(val error: DriveErrorBody? = null)

@Serializable
internal class DriveErrorBody(
    val message: String? = null,
    val errors: List<DriveErrorItem> = emptyList(),
)

@Serializable
internal class DriveErrorItem(@SerialName("reason") val reason: String? = null)

internal fun DriveFileDto.toCloudFile(): CloudFile = CloudFile(
    id = id,
    name = name,
    size = size?.toLongOrNull() ?: 0L,
    createdTime = createdTime?.let { parseRfc3339Millis(it) } ?: 0L,
    parentId = parents.first(),
)

/** Parses a Drive RFC 3339 timestamp (any offset, any fraction) to epoch milliseconds. */
internal fun parseRfc3339Millis(text: String): Long = try {
    Instant.parse(text).toEpochMilliseconds()
} catch (e: IllegalArgumentException) {
    throw IOException("Unparseable Drive timestamp: $text", e)
}

/**
 * Formats epoch milliseconds as the UTC RFC 3339 string the Android client library produced for
 * `DateTime(ms).toStringRfc3339()`: always three fraction digits and a `Z`, for example `1970-01-01T00:00:01.000Z`.
 */
internal fun formatRfc3339Millis(epochMillis: Long): String {
    val days = epochMillis.floorDiv(86_400_000L)
    val msOfDay = epochMillis.mod(86_400_000L)
    // Civil-from-days (Howard Hinnant), valid for the proleptic Gregorian calendar.
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z.mod(146_097L)
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun p(v: Long, n: Int) = v.toString().padStart(n, '0')
    return "${p(year, 4)}-${p(month, 2)}-${p(day, 2)}T" +
        "${p(msOfDay / 3_600_000, 2)}:${p(msOfDay / 60_000 % 60, 2)}:${p(msOfDay / 1000 % 60, 2)}.${p(msOfDay % 1000, 3)}Z"
}
