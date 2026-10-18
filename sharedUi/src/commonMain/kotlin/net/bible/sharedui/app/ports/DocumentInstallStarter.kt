@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface DocumentInstallStarter {
    /** Hands [uris] to the install service; [action] is the Intent action the files arrived with, null for an in-app pick. */
    fun start(uris: List<DocumentRef>, action: String?)
    fun hostBound()
    fun hostUnbound()
}
