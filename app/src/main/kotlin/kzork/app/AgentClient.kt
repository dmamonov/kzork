package kzork.app

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

interface AgentClient {
    suspend fun run(instructions: String, input: String, tools: List<JsonObject> = emptyList(),
                    execute: suspend (String, JsonObject) -> JsonObject = { _, _ -> error("No tools") }): String
}

class ResponsesClient(
    private val key: String, private val model: String, private val maxRequests: Int = 300,
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 20_000 }
    },
) : AgentClient, Closeable {
    private val requests = AtomicInteger()
    override suspend fun run(instructions: String, input: String, tools: List<JsonObject>, execute: suspend (String, JsonObject) -> JsonObject): String {
        val conversation = mutableListOf<JsonElement>(buildJsonObject { put("role", "user"); put("content", input) })
        repeat(10) {
            val payload = buildJsonObject {
                put("model", model); put("instructions", instructions); put("input", JsonArray(conversation))
                put("store", false); put("max_output_tokens", 12000)
                put("include", JsonArray(listOf(JsonPrimitive("reasoning.encrypted_content"))))
                if (tools.isNotEmpty()) {
                    put("parallel_tool_calls", false)
                    put("tools", JsonArray(tools.map { JsonObject(it + ("type" to JsonPrimitive("function")) + ("strict" to JsonPrimitive(false))) }))
                }
            }
            require(requests.incrementAndGet() <= maxRequests) { "AI request budget reached. Restart with a larger --max-requests limit to continue." }
            val response = client.post("https://api.openai.com/v1/responses") {
                bearerAuth(key); contentType(ContentType.Application.Json); setBody(payload.toString())
            }
            if (!response.status.isSuccess()) {
                // Never expose response bodies, request data, or credentials to the browser/logs.
                error("AI service returned HTTP ${response.status.value}. Check account access, model and API configuration.")
            }
            val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
            require(parsed.string("status") != "incomplete") { "AI output limit reached; turn has not been completed" }
            val output = parsed.array("output")
            conversation.addAll(output)
            val calls = output.filter { it.jsonObject.string("type") == "function_call" }
            if (calls.isEmpty()) {
                return output.flatMap { it.jsonObject.array("content") }.filter { it.jsonObject.string("type") == "output_text" }
                    .joinToString("\n") { it.jsonObject.string("text") }.also { require(it.isNotBlank()) { "AI returned no answer" } }
            }
            for (item in calls) {
                val call = item.jsonObject
                val result = try { execute(call.string("name"), json.parseToJsonElement(call.string("arguments")).jsonObject) }
                catch (e: IllegalArgumentException) { buildJsonObject { put("ok", false); put("error", e.message) } }
                conversation.add(buildJsonObject {
                    put("type", "function_call_output"); put("call_id", call.string("call_id")); put("output", result.toString())
                })
            }
        }
        error("Agent exceeded the ten-response tool budget; use Retry to continue a pending update")
    }
    override fun close() { client.close() }
}

fun parseAgentJson(text: String): JsonObject {
    val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return json.parseToJsonElement(clean).jsonObject
}
