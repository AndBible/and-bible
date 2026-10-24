package net.bible.shareddata

import androidx.room3.RoomDatabase
import net.bible.android.database.DbDate
import net.bible.android.database.IdType

/** Fixed id whose 16 stored bytes are `00 11 22 ... ff` (big-endian most/least significant halves). */
val FIXED_ID: IdType = IdType.fromString("00112233-4455-6677-8899-aabbccddeeff")
const val FIXED_ID_HEX = "00112233445566778899AABBCCDDEEFF"

val OTHER_ID: IdType = IdType.fromString("ffeeddcc-bbaa-9988-7766-554433221100")

/** A date whose millisecond part must survive unchanged. */
val FIXED_DATE_MILLIS = 1_700_000_000_123L
val FIXED_DATE = DbDate(FIXED_DATE_MILLIS)

/** ARGB colour with the alpha bit set, i.e. a negative Int as stored by the app. */
val NEGATIVE_ARGB: Int = 0xFF336699.toInt()

/** Runs [block] against a fresh database file and always closes it. */
suspend inline fun <reified T : RoomDatabase> withDb(block: suspend (T) -> Unit) {
    val db = openTestDatabase<T>(testDatabaseDir(), "test.sqlite3")
    try {
        block(db)
    } finally {
        db.close()
    }
}
