/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils.concat
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CustomRepositoriesBinding
import net.bible.android.activity.databinding.CustomRepositoryEditorBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.service.common.CommonUtils.getResourceColor
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.htmlToSpan
import net.bible.service.db.DatabaseContainer
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val customRepositoriesWikiUrl = "https://github.com/AndBible/and-bible/wiki/Custom-repositories"

@Serializable
data class RepositoryData (
    var repository: CustomRepository,
    var delete: Boolean = false,
    var cancel: Boolean = false,
) {
    fun toJSON(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJSON(str: String): RepositoryData = json.decodeFromString(serializer(), str)
    }
}
class CustomRepositoryEditor: CustomTitlebarActivityBase() {
    private lateinit var binding: CustomRepositoryEditorBinding
    private lateinit var data: RepositoryData
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        data = RepositoryData.fromJSON(intent.getStringExtra("data")!!)
        binding = CustomRepositoryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateUI()
        if(data.repository.manifestUrl?.isNotEmpty() == true) {
            delayedValidate()
        }
        binding.run {
            pasteButton.setOnClickListener { paste() }
            manifestUrl.addTextChangedListener  (object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) { delayedValidate() }
            })
        }
    }

    val delayedValidate: () -> Unit = debounce(200, lifecycleScope) {validateSpec()}

    var valid: Boolean = false
        set(value) {
            binding.okCheck.drawable.mutate().setTint(getResourceColor(if(value) R.color.green else (R.color.grey_500)))
            field = value
        }

    private suspend fun tryReadManifest(manifestUrlStr: String): Boolean {
        val manifestUrl = try {
            URL(manifestUrlStr)
        } catch (e: MalformedURLException) {
            return false
        }

        return withContext(Dispatchers.IO) {
            val conn =
                try {
                    manifestUrl.openConnection() as HttpsURLConnection
                } catch (e: IOException) {
                    return@withContext false
                }

            return@withContext if (conn.responseCode == 200) {
                readManifest(conn)
            } else {
                false
            }
        }
    }

    private fun validateSpec() = lifecycleScope.launch {
        Log.i(TAG, "validateSpec")
        val manifestUrlStr = binding.manifestUrl.text.toString()

        if (!manifestUrlStr.startsWith("https://")) {
            valid = false
            return@launch
        }

        var ok = tryReadManifest(manifestUrlStr)
        if(!ok) {
            val filename = "manifest.json"
            val newUrlStr =
                if(manifestUrlStr.endsWith("/"))
                    "$manifestUrlStr$filename"
                else
                    "$manifestUrlStr/$filename"
            ok = tryReadManifest(newUrlStr)
        }

        valid = ok
        if(ok) {
            updateData()
        }
    }

    private fun readManifest(conn: HttpsURLConnection): Boolean {
        Log.i(TAG, "readManifest")
        val jsonString = String(conn.inputStream.readBytes())
        val json = try {JSONObject(jsonString)} catch (e: JSONException) {
            Log.e(TAG, "Error in parsing JSON", e)
            return false
        }
        val type = json.getString("type")
        if(type != "sword-https") return false
        data.repository.manifestJsonContent = jsonString
        val manifest = data.repository.manifest

        if(manifest == null) {
            data.repository.manifestJsonContent = null
            return false
        }

        Log.i(TAG, "Read manifest ${manifest.name}")
        return true
    }

    private fun paste() {
        Log.i(TAG, "paste")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val paste = clipboard.primaryClip?.getItemAt(0)?.text
        if(paste != null) {
            data.repository.manifestUrl = paste.toString()
            updateUI()
            delayedValidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repository_editor_options_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.delete -> delete()
            R.id.help -> help()
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        saveAndExit()
    }

    private fun saveAndExit() {
        Log.i(TAG, "saveAndExit")
        updateData()
        val resultIntent = Intent()
        if (!data.delete && !valid) {
            data.cancel = true
            ABEventBus.post(ToastEvent(R.string.invalid_repository_not_saved))
        }

        resultIntent.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun updateData() = binding.run {
        data.repository.manifestUrl = manifestUrl.text.toString()
    }

    private fun updateUI() = binding.run {
        manifestUrl.setText(data.repository.manifestUrl)
    }

    private fun delete() = lifecycleScope.launch(Dispatchers.Main) {
        val result = suspendCoroutine {
            AlertDialog.Builder(this@CustomRepositoryEditor)
                .setMessage(getString(R.string.delete_custom_repository, data.repository.displayName))
                .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                .setNegativeButton(R.string.no) {_, _ -> it.resume(false)}
                .setCancelable(true)
                .create().show()
        }
        if(result) {
            data.delete = true
            saveAndExit()
        }
    }

    private fun help() {
        val s0 = getString(R.string.custom_repositories_help0)
        val s1 = getString(R.string.custom_repositories_help1)
        val s3 = getString(R.string.wiki_page)
        val urlLink = """<a href="$customRepositoriesWikiUrl">$s3</a>"""
        val s2 = htmlToSpan(getString(R.string.custom_repositories_help2, urlLink))
        val s  = concat(
            s0, "\n\n", s1, "\n\n", s2
        )
        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(R.string.custom_repositories)
            .setMessage(s)
            .create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
    companion object {
        private const val TAG = "CustomRepositories"
    }
}

class CustomRepositories : ListActivityBase() {
    private lateinit var binding: CustomRepositoriesBinding
    private var customRepositories = arrayListOf<CustomRepository>()
    private val dao get() = DatabaseContainer.db.customRepositoryDao()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CustomRepositoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        listAdapter = createAdapter()
        reloadData()
    }

    private fun reloadData() {
        customRepositories.clear()
        customRepositories.addAll(dao.all())
        (listAdapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    /**
     * Creates and returns a list adapter for the current list activity
     * @return
     */
    private fun createAdapter(): ListAdapter {
        return object : ArrayAdapter<CustomRepository>(
            this,
            R.layout.custom_repository_item,
            R.id.titleText, customRepositories
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.findViewById<TextView>(R.id.titleText).text = customRepositories[position].displayName
                view.findViewById<TextView>(R.id.descriptionText).text = customRepositories[position].displayDescription
                return view
            }
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val repo = customRepositories[position]
        val intent = Intent(this@CustomRepositories, CustomRepositoryEditor::class.java)
        intent.putExtra("data", RepositoryData(repo).toJSON())
        lifecycleScope.launch {
            val result = awaitIntent(intent)
            val data = RepositoryData.fromJSON(result.resultData.getStringExtra("data")!!)
            handleResult(data)
        }
    }

    private fun handleResult(data: RepositoryData) {
        if(data.cancel) {
            return
        } else if(data.delete) {
            if(data.repository.id != 0L) {
                dao.delete(data.repository)
            }
        } else {
            dao.upsert(data.repository)
        }
        reloadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repositories_options_menu, menu)
        return true
    }

    private fun help() {
        val s0 = getString(R.string.custom_repositories_help0)
        val s3 = getString(R.string.wiki_page)
        val urlLink = """<a href="$customRepositoriesWikiUrl">$s3</a>"""
        val s2 = htmlToSpan(getString(R.string.custom_repositories_help2, urlLink))
        val s  = concat(
            s0, "\n\n", s2
        )
        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(R.string.custom_repositories)
            .setMessage(s)
            .create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
    private fun newItem() = lifecycleScope.launch {
        val intent = Intent(this@CustomRepositories, CustomRepositoryEditor::class.java)
        intent.putExtra("data", RepositoryData(CustomRepository()).toJSON())
        val result = awaitIntent(intent)
        val data = RepositoryData.fromJSON(result.resultData.getStringExtra("data")!!)
        handleResult(data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.help -> help()
            R.id.newItem -> newItem()
            android.R.id.home -> finish()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        finish()
    }

    companion object {
        private const val TAG = "CustomRepositories"
    }
}
