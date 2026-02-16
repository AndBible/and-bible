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

package net.bible.service.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.service.llm.agent.PermissionMode
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "PromptCsvUtils"

/**
 * CSV Import/Export utilities for AI prompts.
 * Uses semicolon (;) as separator to handle commas in prompt templates.
 * Modeled after BookmarkCsvUtils.
 */
object PromptCsvUtils {

    private const val CSV_SEPARATOR = ";"
    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private const val HEADER_NAME = "name"
    private const val HEADER_DESCRIPTION = "description"
    private const val HEADER_PROMPT_TEMPLATE = "promptTemplate"
    private const val HEADER_SHOW_IN = "showIn"
    private const val HEADER_ORDER_NUMBER = "orderNumber"
    private const val HEADER_STRICT_CONTEXT_MATCHING = "strictContextMatching"
    private const val HEADER_PERMISSION_MODE = "permissionMode"
    private const val HEADER_ALLOWED_TOOLS = "allowedTools"
    private const val HEADER_DENIED_TOOLS = "deniedTools"
    private const val HEADER_MODEL_OVERRIDE = "modelOverride"
    private const val HEADER_ID = "id"
    private const val HEADER_CREATED_AT = "createdAt"

    private val ALL_HEADERS = listOf(
        HEADER_NAME, HEADER_DESCRIPTION, HEADER_PROMPT_TEMPLATE, HEADER_SHOW_IN,
        HEADER_ORDER_NUMBER, HEADER_STRICT_CONTEXT_MATCHING, HEADER_PERMISSION_MODE,
        HEADER_ALLOWED_TOOLS, HEADER_DENIED_TOOLS, HEADER_MODEL_OVERRIDE,
        HEADER_ID, HEADER_CREATED_AT
    )

    /**
     * Export prompts to CSV format.
     */
    suspend fun exportPromptsToCsv(
        outputStream: OutputStream,
        prompts: List<AgentPrompt>
    ) = withContext(Dispatchers.IO) {
        try {
            OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                writer.write(ALL_HEADERS.joinToString(CSV_SEPARATOR))
                writer.write("\n")

                for (prompt in prompts) {
                    val values = listOf(
                        escapeField(prompt.name),
                        escapeField(prompt.description ?: ""),
                        escapeField(prompt.promptTemplate),
                        prompt.showIn.joinToString(",") { it.name },
                        prompt.orderNumber.toString(),
                        prompt.strictContextMatching.toString(),
                        prompt.permissionMode?.name ?: "",
                        prompt.allowedTools?.joinToString(",") ?: "",
                        prompt.deniedTools?.joinToString(",") ?: "",
                        prompt.modelOverride ?: "",
                        prompt.id.toString(),
                        ISO_DATE_FORMAT.format(Date(prompt.createdAt)),
                    )
                    writer.write(values.joinToString(CSV_SEPARATOR))
                    writer.write("\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting prompts to CSV", e)
            throw e
        }
    }

    /**
     * Import prompts from CSV format.
     * - If `id` matches an existing user prompt -> update
     * - If `id` matches a built-in prompt -> skip with error
     * - Otherwise -> create new prompt
     */
    suspend fun importPromptsFromCsv(
        inputStream: InputStream,
    ): ImportResult = withContext(Dispatchers.IO) {
        var created = 0
        var updated = 0
        val errorMessages = mutableListOf<String>()

        try {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                val headerRecord = readCsvRecord(reader) ?: throw IOException("Empty CSV file")
                val headerMap = headerRecord.withIndex().associate { it.value.trim() to it.index }

                var recordNumber = 2
                while (true) {
                    val record = readCsvRecord(reader) ?: break

                    try {
                        if (record.all { it.trim().isEmpty() }) {
                            recordNumber++
                            continue
                        }

                        val prompt = parseCsvRowToPrompt(record, headerMap, recordNumber)

                        if (BuiltInPrompts.isBuiltIn(prompt.id)) {
                            errorMessages.add("Row $recordNumber: Cannot import over built-in prompt '${prompt.name}'")
                            recordNumber++
                            continue
                        }

                        val existing = PromptRepository.promptById(prompt.id)
                        if (existing != null && !BuiltInPrompts.isBuiltIn(existing.id)) {
                            PromptRepository.updatePrompt(prompt)
                            updated++
                        } else {
                            PromptRepository.insertPrompt(prompt)
                            created++
                        }
                    } catch (e: Exception) {
                        errorMessages.add("Row $recordNumber: ${e.message}")
                        Log.w(TAG, "Error importing prompt from row $recordNumber", e)
                    }
                    recordNumber++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing prompts from CSV", e)
            throw e
        }

        ImportResult(created, updated, errorMessages)
    }

    private fun parseCsvRowToPrompt(
        values: List<String>,
        headerMap: Map<String, Int>,
        lineNumber: Int
    ): AgentPrompt {
        val name = getValueOrNull(values, headerMap, HEADER_NAME)?.trim()
        if (name.isNullOrEmpty()) {
            throw IllegalArgumentException("Missing required field 'name'")
        }

        val promptTemplate = getValueOrNull(values, headerMap, HEADER_PROMPT_TEMPLATE)
        if (promptTemplate.isNullOrEmpty()) {
            throw IllegalArgumentException("Missing required field 'promptTemplate'")
        }

        val id = getValueOrNull(values, headerMap, HEADER_ID)?.let {
            if (it.isNotEmpty()) IdType(it) else IdType()
        } ?: IdType()

        val description = getValueOrNull(values, headerMap, HEADER_DESCRIPTION)?.let {
            it.ifEmpty { null }
        }

        val showIn = getValueOrNull(values, headerMap, HEADER_SHOW_IN)?.let { str ->
            if (str.isNotEmpty()) {
                str.split(",").mapNotNull { s ->
                    try { PromptContext.valueOf(s.trim()) } catch (_: IllegalArgumentException) { null }
                }.toSet()
            } else emptySet()
        } ?: emptySet()

        val orderNumber = getValueOrNull(values, headerMap, HEADER_ORDER_NUMBER)
            ?.toIntOrNull() ?: 0

        val strictContextMatching = getValueOrNull(values, headerMap, HEADER_STRICT_CONTEXT_MATCHING)
            ?.lowercase()?.toBooleanStrictOrNull() ?: true

        val permissionMode = getValueOrNull(values, headerMap, HEADER_PERMISSION_MODE)?.let { str ->
            if (str.isNotEmpty()) {
                try { PermissionMode.valueOf(str.trim()) } catch (_: IllegalArgumentException) { null }
            } else null
        }

        val allowedTools = getValueOrNull(values, headerMap, HEADER_ALLOWED_TOOLS)?.let { str ->
            if (str.isNotEmpty()) str.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else null
        }

        val deniedTools = getValueOrNull(values, headerMap, HEADER_DENIED_TOOLS)?.let { str ->
            if (str.isNotEmpty()) str.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else null
        }

        val modelOverride = getValueOrNull(values, headerMap, HEADER_MODEL_OVERRIDE)?.let {
            it.ifEmpty { null }
        }

        val createdAt = getValueOrNull(values, headerMap, HEADER_CREATED_AT)?.let { str ->
            if (str.isNotEmpty()) {
                try { ISO_DATE_FORMAT.parse(str)?.time } catch (_: Exception) { null }
            } else null
        } ?: System.currentTimeMillis()

        return AgentPrompt(
            id = id,
            name = name,
            description = description,
            promptTemplate = promptTemplate,
            showIn = showIn,
            orderNumber = orderNumber,
            strictContextMatching = strictContextMatching,
            permissionMode = permissionMode,
            allowedTools = allowedTools,
            deniedTools = deniedTools,
            modelOverride = modelOverride,
            createdAt = createdAt,
        )
    }

    private fun getValueOrNull(values: List<String>, headerMap: Map<String, Int>, header: String): String? {
        val index = headerMap[header] ?: return null
        return if (index < values.size) values[index] else null
    }

    private fun escapeField(field: String): String {
        return if (field.contains(CSV_SEPARATOR) || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * Reads a complete CSV record that may span multiple lines (handles quoted fields with newlines).
     */
    private fun readCsvRecord(reader: BufferedReader): List<String>? {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        while (true) {
            val line = reader.readLine() ?: break

            var i = 0
            while (i < line.length) {
                val char = line[i]
                when {
                    char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i += 2
                        continue
                    }
                    char == '"' -> {
                        inQuotes = !inQuotes
                    }
                    char == ';' && !inQuotes -> {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    else -> {
                        current.append(char)
                    }
                }
                i++
            }

            if (inQuotes) {
                current.append('\n')
            } else {
                break
            }
        }

        if (result.isEmpty() && current.isEmpty()) {
            return null
        }

        result.add(current.toString())
        return result
    }

    data class ImportResult(
        val created: Int,
        val updated: Int,
        val errorMessages: List<String>,
    ) {
        val errors: Int get() = errorMessages.size
    }
}
