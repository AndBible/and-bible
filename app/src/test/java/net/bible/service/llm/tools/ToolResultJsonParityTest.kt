package net.bible.service.llm.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import net.bible.android.TEST_SDK
import net.bible.service.llm.tools.read.GetWindowsTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class ToolResultJsonParityTest {
    @Test
    fun supportedResultsMatchLegacyStructure() {
        val results = listOf(
            typedSuccess(GetWindowsTool.Result(0, "id", emptyList())),
            typedSuccess(FinishWithoutDocumentTool.Result(true, "a/b", "done")),
            ToolResult.success(listOf("a", 1, null)),
            ToolResult.success(mapOf("k" to listOf(1, 2))),
            ToolResult.success("s"), ToolResult.success(3), ToolResult.success(true),
            ToolResult.error("m"), ToolResult.error("m", "CODE")
        )
        results.forEach { result ->
            assertEquals(result.toString(), Json.parseToJsonElement(legacyToJson(result)), Json.parseToJsonElement(result.toJson()))
        }
        // Build both sides independently so a dropped builder field cannot change the oracle.
        val builderResult = ToolResult.success { put("key", "a/b"); put("count", 2) }
        val legacyBuilderResult = ToolResult.Success(JSONObject().apply {
            put("key", "a/b")
            put("count", 2)
        })
        assertEquals(Json.parseToJsonElement(legacyToJson(legacyBuilderResult)), Json.parseToJsonElement(builderResult.toJson()))
    }

    // Frozen pre-migration implementation: independent structural serialization oracle.
    private fun legacyToJson(result: ToolResult): String = when (result) {
        is ToolResult.Success -> {
            val json = JSONObject()
            json.put("status", "success")
            if (result.jsonOverride != null) {
                json.put("data", JSONObject(result.jsonOverride))
            } else when (val data = result.data) {
                is JSONObject -> json.put("data", data)
                is JSONArray -> json.put("data", data)
                is String -> json.put("data", data)
                is Number -> json.put("data", data)
                is Boolean -> json.put("data", data)
                is List<*> -> json.put("data", JSONArray(data))
                is Map<*, *> -> json.put("data", JSONObject(data))
                else -> json.put("data", data.toString())
            }
            json.toString()
        }
        is ToolResult.Error -> {
            val json = JSONObject()
            json.put("status", "error")
            json.put("message", result.message)
            result.code?.let { json.put("code", it) }
            json.toString()
        }
    }
}
