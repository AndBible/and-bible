/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CustomRepositoryEditorBinding
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.appendUrl
import net.bible.service.common.CommonUtils.md5Hash
import net.bible.service.common.htmlToSpan
import net.bible.service.sword.mybible.MyBibleRepositorySpec
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Serializable
data class RepositoryData (
    var repository: CustomRepository? = null,
    var delete: Boolean = false,
    var cancel: Boolean = false,
) {
    fun toJSON(): String = CommonUtils.json.encodeToString(serializer(), this)

    companion object {
        fun fromJSON(str: String): RepositoryData = CommonUtils.json.decodeFromString(serializer(), str)
    }
}
class CustomRepositoryEditor: CustomTitlebarActivityBase() {
    private lateinit var binding: CustomRepositoryEditorBinding
    private lateinit var data: RepositoryData
    private lateinit var initialDataJson: String
    private var saveMenuItem: MenuItem? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        data = RepositoryData.fromJSON(intent.getStringExtra("data")!!)
        binding = CustomRepositoryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initialDataJson = data.toJSON()
        updateUI()
        if(data.repository?.manifestUrl?.isNotEmpty() == true) {
            binding.manifestUrl.setText(data.repository?.manifestUrl?: "")
            delayedValidate()
        }
        binding.run {
            pasteButton.setOnClickListener { paste() }
            var oldText: String = ""
            manifestUrl.addTextChangedListener  (object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                }
                override fun afterTextChanged(s: Editable) {
                    if(oldText != s.toString()) {
                        delayedValidate()
                    }
                    oldText = s.toString()
                }
            })
        }
    }

    val delayedValidate: () -> Unit = debounce(200, lifecycleScope) {validateManifestUrl()}

    var valid: Boolean = false
        set(value) {
            binding.okCheck.visibility = if(value) View.VISIBLE else View.INVISIBLE
            saveMenuItem?.isEnabled = value
            field = value
        }

    private suspend fun checkCanRead(urlStr: String): Boolean {
        val manifestUrl = try {
            URL(urlStr)
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
            val responseCode = try {conn.responseCode} catch(e: IOException) {null}
            return@withContext responseCode == 200
        }
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
            val responseCode = try {conn.responseCode} catch (e: IOException) { null }
            return@withContext if (responseCode == 200) {
                readManifest(conn)
            } else {
                false
            }
        }
    }

    private fun validateManifestUrl() = lifecycleScope.launch {
        Log.i(TAG, "validateSpec")
        binding.loadingIndicator.visibility = View.VISIBLE
        val manifestUrl = manifestUrl
        if (!manifestUrl.startsWith("https://")) {
            valid = false
            binding.loadingIndicator.visibility = View.GONE
            return@launch
        }

        var ok = tryReadManifest(manifestUrl)
        if(!ok) {
            val newUrlStr = appendUrl(manifestUrl, "manifest.json")
            ok = tryReadManifest(newUrlStr)
        }
        if(!ok) {
            val url = URL(manifestUrl)

            val packagesUrl = appendUrl(manifestUrl, "packages")
            val modsIndexUrl = appendUrl(manifestUrl, "mods.d.tar.gz")

            val (manifestOk, packagesOk, modsIndexOk) = awaitAll(
                async(Dispatchers.IO) {checkCanRead(manifestUrl)},
                async(Dispatchers.IO) {checkCanRead(packagesUrl)},
                async(Dispatchers.IO) {checkCanRead(modsIndexUrl)}
            )
            ok = manifestOk && packagesOk && modsIndexOk

            if(ok) {
                val repo = CustomRepository(
                    name = "${url.host}-${md5Hash(manifestUrl).subSequence(0, 3)}",
                    description = manifestUrl,
                    manifestUrl = manifestUrl,
                    host = url.host,
                    catalogDirectory = url.path,
                    packageDirectory = appendUrl(url.path, "packages"),
                    type = "sword-https",
                    id = data.repository?.id?: 0
                )
                data.repository = repo
            }
        }
        valid = ok
        binding.loadingIndicator.visibility = View.GONE
        updateUI()
    }

    private fun readManifest(conn: HttpsURLConnection): Boolean {
        Log.i(TAG, "readManifest")
        val jsonString = String(conn.inputStream.readBytes())
        val json = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            Log.e(TAG, "Error in parsing JSON", e)
            return false
        }
        var repo: CustomRepository? = null

        val type = try { json.getString("type") } catch (e: JSONException){ null }

        if(type == "sword-https") {
            repo = CustomRepository.fromJson(jsonString) ?: return false
        } else {
            val myBibleSpec = try {
                MyBibleRepositorySpec.fromJson(jsonString)
            } catch (e: SerializationException) {
                null
            }
            if(myBibleSpec != null) {
                repo = CustomRepository(
                    name = myBibleSpec.file_name,
                    description = myBibleSpec.description,
                    type = "mybible-https",
                    manifestUrl = myBibleSpec.url,
                )
            }
        }

        if(repo == null) return false

        repo.id = data.repository?.id?: 0
        data.repository = repo
        Log.i(TAG, "Read manifest ${repo.name}")
        return true
    }

    private val manifestUrl get() = binding.manifestUrl.text.toString()

    private fun paste() {
        Log.i(TAG, "paste")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val paste = clipboard.primaryClip?.getItemAt(0)?.text
        if(paste != null) {
            binding.manifestUrl.setText(paste.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_repository_editor_options_menu, menu)
        saveMenuItem = menu.findItem(R.id.save)
        saveMenuItem?.isEnabled = valid
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId){
            R.id.save -> saveAndExit()
            R.id.delete -> delete()
            R.id.help -> help()
            android.R.id.home -> cancelOrConfirmDiscard()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        cancelOrConfirmDiscard()
    }

    private fun saveAndExit() {
        Log.i(TAG, "saveAndExit")
        updateData()
        val resultIntent = Intent()
        resultIntent.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun isDirty(): Boolean {
        updateData()
        return data.toJSON() != initialDataJson
    }

    private fun cancelOrConfirmDiscard() {
        if (isDirty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> cancelAndExit() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            cancelAndExit()
        }
    }

    private fun cancelAndExit() {
        data.cancel = true
        val resultIntent = Intent()
        resultIntent.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun updateData() {
        val repo = data.repository?: return
        repo.packageDirectory = binding.packageDir.text.toString()
    }

    private fun updateUI() = binding.run {
        val repo = data.repository
        if(repo == null || !valid) {
            infoText.text = ""
            packageDir.setText("")
        } else {
            infoText.text = TextUtils.concat(
                repo.name,
                "\n\n",
                repo.description,
            )
            packageDir.setText(repo.packageDirectory)
        }
    }

    private fun delete() = lifecycleScope.launch(Dispatchers.Main) {
        val result = suspendCoroutine {
            AlertDialog.Builder(this@CustomRepositoryEditor)
                .setMessage(getString(R.string.delete_custom_repository, data.repository?.name))
                .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                .setNegativeButton(R.string.no) { _, _ -> it.resume(false)}
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
        val s  = TextUtils.concat(
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
