package net.bible.sharedcore.bibleview

/** Records every bridge call as (method, arguments); deferred calls do not answer. */
class RecordingBridge : UnimplementedBibleBridge({}, {}) {
    val calls = mutableListOf<Pair<String, List<Any?>>>()
    override fun unimplemented(method: String, args: List<Any?>) { calls += method to args }
    override fun unimplementedDeferred(method: String, callId: Long, args: List<Any?>) { calls += method to args }
}
