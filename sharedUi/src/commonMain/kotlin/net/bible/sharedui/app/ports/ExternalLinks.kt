package net.bible.sharedui.app.ports

/** Opening web links; discrete builds ask the user first. */
interface ExternalLinks {
    val askBeforeOpening: Boolean
    fun open(url: String)
}
