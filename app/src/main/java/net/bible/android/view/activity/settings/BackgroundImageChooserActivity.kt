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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BackgroundImageChooserBinding
import net.bible.android.activity.databinding.BackgroundImageChooserItemBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.AndBibleAddons
import java.io.File

private class ImageEntry(val initials: String, val name: String, val file: File)

class BackgroundImageChooserActivity : ActivityBase() {
    private lateinit var binding: BackgroundImageChooserBinding
    private lateinit var adapter: Adapter

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from InstallZip, refresh so a freshly imported image appears.
        AndBibleAddons.clearCaches()
        refresh()
    }

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
                importLauncher.launch(Intent(this@BackgroundImageChooserActivity, InstallZip::class.java))
            }
            noneButton.setOnClickListener { finishWith(null) }
        }
        refresh()
    }

    private fun refresh() {
        val images = AndBibleAddons.providedBackgroundImages.map { (initials, p) ->
            ImageEntry(initials, p.name, p.file)
        }.sortedBy { it.name.lowercase() }
        adapter.submit(images)
        binding.emptyLabel.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE
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
