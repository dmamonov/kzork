package kzork.app

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ResponsesClientTest {
    @Test fun `responses tool outputs and reasoning are sent on continuation`() = runBlocking {
        var requests = 0
        val engine = MockEngine { request ->
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            val body = json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(JsonPrimitive(false), body["store"])
            requests++
            val reply = if (requests == 1) {
                assertEquals("function", body.array("tools").single().jsonObject.string("type"))
                """{"status":"completed","output":[{"type":"reasoning","id":"r1","encrypted_content":"opaque","summary":[]},{"type":"function_call","call_id":"c1","name":"read_page","arguments":"{\"path\":\"state.md\"}"}]}"""
            } else {
                val items = body.array("input")
                assertTrue(items.any { it.jsonObject.string("type") == "reasoning" })
                val result = items.last().jsonObject
                assertEquals("function_call_output", result.string("type"))
                assertEquals("c1", result.string("call_id"))
                """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Applied"}]}]}"""
            }
            respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        ResponsesClient("test-key", "test-model", client = HttpClient(engine)).use { ai ->
            val answer = ai.run("test", "test", listOf(buildJsonObject { put("name", "read_page") })) { name, arguments ->
                assertEquals("read_page", name); assertEquals("state.md", arguments.string("path"))
                buildJsonObject { put("ok", true) }
            }
            assertEquals("Applied", answer); assertEquals(2, requests)
        }
    }
    @Test fun `service errors do not leak response bodies`() = runBlocking {
        val engine = MockEngine { respond("sensitive-provider-details", HttpStatusCode.Unauthorized) }
        ResponsesClient("test-key", "test", client = HttpClient(engine)).use { ai ->
            val error = assertFails { ai.run("test", "test") }
            assertTrue(error.message!!.contains("401")); assertFalse(error.message!!.contains("sensitive"))
        }
    }
}
