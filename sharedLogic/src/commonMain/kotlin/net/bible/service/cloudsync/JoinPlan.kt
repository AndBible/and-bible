package net.bible.service.cloudsync

/** What a category's join needs, as found by the read-only [CloudSync.probeJoin]. */
sealed interface JoinState {
    /** Folder known and joined, nothing to ask. */
    data object Ready : JoinState
    /** Interrupted merge files present; execute recovers the database and disables the category. */
    data object NeedsRecovery : JoinState
    /** No cloud folder: create and upload, no question. */
    data object NeedsCreate : JoinState
    /** A cloud folder exists but this device has not joined it: the user decides (fetch, merge or create anew). */
    data class NeedsDecision(val preliminaryFolderId: String) : JoinState
}

/**
 * Result of [CloudSync.probeJoin]. [resetNamespace] and [dropStaleState] are the stored-state clean-ups that
 * [CloudSync.executeJoin] applies before acting; the probe itself writes nothing.
 */
data class JoinProbe(
    val category: SyncableDatabaseDefinition,
    val state: JoinState,
    val resetNamespace: Boolean,
    val dropStaleState: Boolean,
    /** False for the placeholder [JoinState.NeedsCreate] of a probe that did not list the cloud: it says nothing about the cloud and must not consume a stored decision. */
    val cloudListed: Boolean = true,
)

/** The answers of the join plan dialog; [value] is the [net.bible.sharedcore.ui.dialog.DialogAnswer.value] on the wire. */
enum class JoinChoice(val value: String) {
    MERGE("merge"), FETCH("fetch"), CREATE("create"), DISABLE("disable");

    /** The operation this choice runs; [DISABLE] runs none (null) and leaves the category off. */
    fun toOperation(): CloudSync.InitialOperation? = when (this) {
        MERGE -> CloudSync.InitialOperation.MERGE
        FETCH -> CloudSync.InitialOperation.FETCH_INITIAL
        CREATE -> CloudSync.InitialOperation.CREATE_NEW
        DISABLE -> null
    }

    companion object {
        fun fromValue(value: String?): JoinChoice? = entries.firstOrNull { it.value == value }
    }
}
