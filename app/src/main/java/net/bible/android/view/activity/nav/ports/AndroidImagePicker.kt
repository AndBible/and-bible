package net.bible.android.view.activity.nav.ports

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import net.bible.sharedui.app.ports.ImagePicker

/**
 * [ImagePicker] over the photo picker: a constructor-time `registerForActivityResult` (it must be registered
 * before RESUMED) bridged to a suspend function through a [CancellableContinuation].
 */
class AndroidImagePicker(activity: ComponentActivity) : ImagePicker {
    private var pending: CancellableContinuation<String?>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pending?.resume(uri?.toString())
        pending = null
    }

    internal val registeredLauncher: ActivityResultLauncher<*> get() = launcher

    override suspend fun pickImage(): String? = suspendCancellableCoroutine { cont ->
        pending = cont
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        cont.invokeOnCancellation { pending = null }
    }
}
