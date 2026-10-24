package net.bible.android.view.activity.nav.ports

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import net.bible.service.installzip.DocumentInstallService
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentRef

class AndroidDocumentInstallStarter(private val context: Context) : DocumentInstallStarter {
    override fun start(uris: List<DocumentRef>, action: String?) {
        ContextCompat.startForegroundService(
            context,
            DocumentInstallService.enqueueIntent(context, uris.map { Uri.parse(it.uri) }, action),
        )
    }

    override fun hostBound() = DocumentInstallService.hostBound()
    override fun hostUnbound() = DocumentInstallService.hostUnbound()
}
