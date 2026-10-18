package net.bible.android.view.activity.nav.ports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.NoDocumentPickerException

/**
 * [DocumentPicker] over `registerForActivityResult`. The launchers are registered in the constructor, so the
 * instance must be created while the Activity is still CREATED (a field initializer), or the registry throws.
 * One pick is in flight at a time per launcher: a newer pick answers the older one empty/null, as does a
 * cancelled pick.
 */
class AndroidDocumentPicker(activity: ComponentActivity) : DocumentPicker {
    private var pendingOpenMultiple: CompletableDeferred<List<Uri>>? = null
    private var pendingOpenSingle: CompletableDeferred<Uri?>? = null
    private var pendingCreate: CompletableDeferred<Uri?>? = null
    private var pendingTree: CompletableDeferred<Uri?>? = null

    /**
     * A result that arrived with no one awaiting it: the Activity was recreated while a picker was open, and the
     * registry redelivered the result to the launcher of the new instance (same key) before any host asked for it.
     * Kept until [resumeOpenDocuments] / [resumeDocumentTree] claims it; null inside means a cancelled pick.
     */
    private class Orphan<T>(val value: T)
    private var orphanOpenMultiple: Orphan<List<Uri>>? = null
    private var orphanTree: Orphan<Uri?>? = null

    private val openMultipleLauncher = activity.registerForActivityResult(OpenWithMimeTypes(multiple = true)) {
        val pending = pendingOpenMultiple
        pendingOpenMultiple = null
        if (pending != null) pending.complete(it.orEmpty()) else orphanOpenMultiple = Orphan(it.orEmpty())
    }
    private val openSingleLauncher = activity.registerForActivityResult(OpenWithMimeTypes(multiple = false)) {
        pendingOpenSingle?.complete(it.firstOrNull())
        pendingOpenSingle = null
    }
    private val createLauncher = activity.registerForActivityResult(CreateWithMimeType()) {
        pendingCreate?.complete(it)
        pendingCreate = null
    }
    private val treeLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        val pending = pendingTree
        pendingTree = null
        if (pending != null) pending.complete(it) else orphanTree = Orphan(it)
    }

    override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef> {
        val types = mimeTypes.ifEmpty { listOf("*/*") }
        return if (multiple) {
            CompletableDeferred<List<Uri>>().also { pendingOpenMultiple?.complete(emptyList()); pendingOpenMultiple = it }
                .also { launchOrThrow({ pendingOpenMultiple = null }) { openMultipleLauncher.launch(types) } }.await().map { DocumentRef(it.toString()) }
        } else {
            val uri = CompletableDeferred<Uri?>().also { pendingOpenSingle?.complete(null); pendingOpenSingle = it }
                .also { launchOrThrow({ pendingOpenSingle = null }) { openSingleLauncher.launch(types) } }.await()
            listOfNotNull(uri?.let { DocumentRef(it.toString()) })
        }
    }

    /** Runs [launch]; an `ActivityNotFoundException` clears the pending pick ([clear]) and becomes [NoDocumentPickerException]. */
    private inline fun launchOrThrow(clear: () -> Unit, launch: () -> Unit) {
        try {
            launch()
        } catch (e: android.content.ActivityNotFoundException) {
            clear()
            throw NoDocumentPickerException(e)
        }
    }

    override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? =
        CompletableDeferred<Uri?>().also { pendingCreate?.complete(null); pendingCreate = it }
            .also { createLauncher.launch(suggestedName to mimeType) }.await()?.let { DocumentRef(it.toString()) }

    override suspend fun openDocumentTree(): DocumentRef? =
        CompletableDeferred<Uri?>().also { pendingTree?.complete(null); pendingTree = it }
            .also { treeLauncher.launch(null) }.await()?.let { DocumentRef(it.toString()) }

    override suspend fun resumeOpenDocuments(): List<DocumentRef> {
        orphanOpenMultiple?.let { orphanOpenMultiple = null; return it.value.map { uri -> DocumentRef(uri.toString()) } }
        // Nothing buffered yet: await the redelivered result without launching anything.
        return CompletableDeferred<List<Uri>>().also { pendingOpenMultiple?.complete(emptyList()); pendingOpenMultiple = it }
            .await().map { DocumentRef(it.toString()) }
    }

    override suspend fun resumeDocumentTree(): DocumentRef? {
        orphanTree?.let { orphanTree = null; return it.value?.let { uri -> DocumentRef(uri.toString()) } }
        return CompletableDeferred<Uri?>().also { pendingTree?.complete(null); pendingTree = it }
            .await()?.let { DocumentRef(it.toString()) }
    }

    /** `ACTION_OPEN_DOCUMENT` with an explicit MIME type list, single or multiple. */
    private class OpenWithMimeTypes(private val multiple: Boolean) : ActivityResultContract<List<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: List<String>) =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (input.size == 1) input[0] else "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, input.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            }

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
            if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
            val clip = intent.clipData
            return if (clip != null) (0 until clip.itemCount).map { clip.getItemAt(it).uri } else listOfNotNull(intent.data)
        }
    }

    /** `ACTION_CREATE_DOCUMENT` taking the suggested name and MIME type per call. */
    private class CreateWithMimeType : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>) =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = input.second
                putExtra(Intent.EXTRA_TITLE, input.first)
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
    }
}
