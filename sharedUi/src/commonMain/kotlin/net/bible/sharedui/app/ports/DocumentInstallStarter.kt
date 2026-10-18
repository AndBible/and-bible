package net.bible.sharedui.app.ports

interface DocumentInstallStarter {
    fun start(uris: List<DocumentRef>, action: String)
    fun hostBound()
    fun hostUnbound()
}
