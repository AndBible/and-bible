/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.LlmCacheEntryDetailBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProcessingDao
import java.text.DateFormat
import java.util.Date

class CacheEntryDetailActivity : ActivityBase() {

    private lateinit var binding: LlmCacheEntryDetailBinding
    private var rawXml: String? = null

    private val dao: LlmProcessingDao get() = DatabaseContainer.instance.llmProcessingDb.llmProcessingDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LlmCacheEntryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        val documentInitials = intent.getStringExtra(EXTRA_DOCUMENT_INITIALS) ?: return finish()
        val keyName = intent.getStringExtra(EXTRA_KEY_NAME) ?: return finish()
        val processingType = intent.getStringExtra(EXTRA_PROCESSING_TYPE) ?: return finish()
        val processingParams = intent.getStringExtra(EXTRA_PROCESSING_PARAMS) ?: return finish()

        title = getString(R.string.llm_cache_raw_title, documentInitials, keyName)

        lifecycleScope.launch {
            val entry = withContext(Dispatchers.IO) {
                dao.getFullEntry(documentInitials, keyName, processingType, processingParams)
            }

            if (entry == null) {
                binding.metadataText.text = ""
                binding.rawXmlText.text = getString(R.string.llm_cache_raw_empty)
                return@launch
            }

            rawXml = entry.processedXml

            val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                .format(Date(entry.createdAt))
            val sizeStr = Formatter.formatShortFileSize(this@CacheEntryDetailActivity, entry.processedXml.length.toLong())

            binding.metadataText.text = buildString {
                appendLine(getString(R.string.llm_cache_detail_document, entry.documentInitials))
                appendLine(getString(R.string.llm_cache_detail_key, entry.keyName))
                appendLine(getString(R.string.llm_cache_detail_type, entry.processingType))
                appendLine(getString(R.string.llm_cache_detail_params, entry.processingParams))
                appendLine(getString(R.string.llm_cache_detail_model, entry.modelId))
                appendLine(getString(R.string.llm_cache_detail_date, dateStr))
                appendLine(getString(R.string.llm_cache_detail_size, sizeStr))
                if (entry.languageCode != null) {
                    append(getString(R.string.llm_cache_detail_language, entry.languageCode))
                }
            }

            binding.rawXmlText.text = entry.processedXml.ifBlank {
                getString(R.string.llm_cache_raw_empty)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.llm_cache_entry_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.copyRaw -> {
            val xml = rawXml
            if (xml != null) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("raw xml", xml))
                Toast.makeText(this, R.string.llm_cache_raw_copied, Toast.LENGTH_SHORT).show()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_DOCUMENT_INITIALS = "documentInitials"
        const val EXTRA_KEY_NAME = "keyName"
        const val EXTRA_PROCESSING_TYPE = "processingType"
        const val EXTRA_PROCESSING_PARAMS = "processingParams"
    }
}
