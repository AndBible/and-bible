package net.bible.service.legacymigration.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import net.bible.service.legacymigration.LegacyMigrationException
import okio.FileSystem
import okio.Path

/** Where a migration attempt stands; written to `state.json` at each transition. */
enum class MigrationPhase {
    /** Copying, reading, mapping and validating; nothing outside the migration directory has changed. */
    IN_PROGRESS,

    /** The staged databases are validated and about to be adopted. Nothing outside the migration directory has changed yet. */
    STAGED,

    /** [MigrationState.plan] is written and files may already be moved into the databases directory. Needs a rollback after a crash. */
    ADOPTING,
}

/**
 * One file the adopter intends to move into the databases directory. [replaces] is true when an empty database of the
 * same name was there at planning time and is moved aside first (to `replaced/`), so that a rollback can put it back.
 */
data class PlannedMove(val name: String, val replaces: Boolean)

/**
 * The content of `state.json`: the [phase], how many attempts were started ([attempt], counted when an attempt starts, so
 * a crashed attempt counts), when the current one started, the kind of the last failure (for `GaveUp`), and, while
 * [MigrationPhase.ADOPTING], the adoption [plan] (written before the first move) and the names already [moved] (progress
 * information; the rollback does not depend on it).
 */
data class MigrationState(
    val phase: MigrationPhase,
    val attempt: Int,
    val startedAt: Long,
    val lastKind: LegacyMigrationException.Kind? = null,
    val plan: List<PlannedMove> = emptyList(),
    val moved: List<String> = emptyList(),
) {
    fun toJson(): String = buildJsonObject {
        put("phase", phase.name)
        put("attempt", attempt)
        put("startedAt", startedAt)
        lastKind?.let { put("lastKind", it.name) }
        put("plan", JsonArray(plan.map { buildJsonObject { put("name", it.name); put("replaces", it.replaces) } }))
        put("moved", JsonArray(moved.map { JsonPrimitive(it) }))
    }.toString()

    companion object {
        /** Parses `state.json`; throws on anything malformed (the caller decides what a broken state file means). */
        fun fromJson(text: String): MigrationState {
            val o: JsonObject = Json.parseToJsonElement(text).jsonObject
            return MigrationState(
                phase = MigrationPhase.valueOf(o.getValue("phase").jsonPrimitive.content),
                attempt = o.getValue("attempt").jsonPrimitive.longOrNull!!.toInt(),
                startedAt = o.getValue("startedAt").jsonPrimitive.longOrNull!!,
                lastKind = o["lastKind"]?.jsonPrimitive?.contentOrNull?.let { LegacyMigrationException.Kind.valueOf(it) },
                plan = o["plan"]?.jsonArray.orEmpty().map {
                    PlannedMove(it.jsonObject.getValue("name").jsonPrimitive.content, it.jsonObject.getValue("replaces").jsonPrimitive.booleanOrNull!!)
                },
                moved = o["moved"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
            )
        }
    }
}

/**
 * Reads and writes `state.json` in [dir]. A write is atomic (a temporary file, then `atomicMove`), so a kill leaves either the
 * old or the new complete file, never a torn one.
 */
class MigrationStateStore(private val fs: FileSystem, private val dir: Path) {
    private val file = dir / "state.json"
    private val temp = dir / "state.json.tmp"

    /** The stored state; null when there is none. A file that cannot be parsed counts as an exhausted attempt budget ([UNREADABLE]). */
    fun read(): MigrationState? {
        if (!fs.exists(file)) return null
        return try {
            MigrationState.fromJson(fs.read(file) { readUtf8() })
        } catch (e: Exception) {
            UNREADABLE
        }
    }

    fun write(state: MigrationState) {
        fs.createDirectories(dir)
        fs.write(temp) { writeUtf8(state.toJson()) }
        fs.atomicMove(temp, file)
    }

    /** Removes the state file and a leftover temporary one. */
    fun delete() {
        fs.delete(temp, mustExist = false)
        fs.delete(file, mustExist = false)
    }

    companion object {
        /**
         * What an unparseable `state.json` stands for: two attempts used, nothing to roll back (the plan is lost). The migrator
         * then gives up and the recovery screen takes over, instead of guessing.
         */
        val UNREADABLE = MigrationState(MigrationPhase.IN_PROGRESS, attempt = 2, startedAt = 0)
    }
}
