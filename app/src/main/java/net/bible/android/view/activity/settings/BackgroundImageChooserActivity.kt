/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.settings

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BackgroundImageChooserBinding
import net.bible.android.activity.databinding.BackgroundImageChooserItemBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleAddons
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import java.io.File
import java.io.FileOutputStream

private const val TAG = "BackgroundImageChooser"
private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

private class ImageEntry(val initials: String, val name: String, val file: File)

class BackgroundImageChooserActivity : ActivityBase() {
    private lateinit var binding: BackgroundImageChooserBinding
    private lateinit var adapter: Adapter

    // Android system photo picker (gallery), not a document/file browser.
    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) importImage(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BackgroundImageChooserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)
        title = getString(R.string.background_image_title)

        adapter = Adapter { finishWith(it.initials) }
        binding.apply {
            recyclerView.layoutManager = GridLayoutManager(this@BackgroundImageChooserActivity, 2)
            recyclerView.adapter = adapter
            importButton.setOnClickListener {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            noneButton.setOnClickListener { finishWith(null) }
        }
        refresh()
    }

    /**
     * Up must simply cancel the chooser. Left to AppCompat it would instead navigate to the
     * manifest `parentActivityName`, i.e. re-launch ColorSettingsActivity with a synthesized
     * Intent carrying no `settingsBundle` extra - which used to crash it (#3867).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if(item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)

    private fun refresh() {
        val images = AndBibleAddons.providedBackgroundImages.map { (initials, p) ->
            ImageEntry(initials, p.name, p.file)
        }.sortedBy { it.name.lowercase() }
        adapter.submit(images)
        binding.emptyLabel.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Copy the picked gallery image into the background-image module dir and register it. */
    private fun importImage(uri: Uri) {
        binding.importButton.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { copyAndRegister(uri) }
            binding.importButton.isEnabled = true
            if (ok) {
                AndBibleAddons.clearCaches()
                refresh()
            } else {
                Toast.makeText(this@BackgroundImageChooserActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyAndRegister(uri: Uri): Boolean {
        return try {
            val outDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).apply { mkdirs() }
            val outFile = uniqueFile(outDir, fileName(uri))
            val input = contentResolver.openInputStream(uri) ?: return false
            input.use { FileOutputStream(outFile).use { output -> it.copyTo(output) } }
            addManuallyInstalledBackgroundImageBooks()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import background image", e)
            false
        }
    }

    /** A safe on-disk file name for the picked image: display name with an image extension. */
    private fun fileName(uri: Uri): String {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
        }
        val base = displayName?.takeIf { it.isNotBlank() } ?: "image"
        if (base.substringAfterLast('.', "").lowercase() in imageExtensions) return base
        val ext = when (contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return "$base.$ext"
    }

    /** Avoid clobbering an existing image another workspace may reference. */
    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "${stem}_$i" else "${stem}_$i.$ext")
            i++
        }
        return candidate
    }

    private fun finishWith(initials: String?) {
        setResult(Activity.RESULT_OK, Intent().putExtra("selectedInitials", initials))
        finish()
    }

    private inner class Adapter(val onClick: (ImageEntry) -> Unit) :
        RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<ImageEntry> = emptyList()

        fun submit(newItems: List<ImageEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(val itemBinding: BackgroundImageChooserItemBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = BackgroundImageChooserItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.itemBinding.apply {
                label.text = item.name
                thumbnail.setImageBitmap(decodeThumbnail(item.file))
                root.setOnClickListener { onClick(item) }
            }
        }

        private fun decodeThumbnail(file: File) = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, this)
            var sample = 1
            while (outWidth / sample > 240 || outHeight / sample > 240) sample *= 2
            inJustDecodeBounds = false
            inSampleSize = sample
            BitmapFactory.decodeFile(file.path, this)
        }
    }
}
