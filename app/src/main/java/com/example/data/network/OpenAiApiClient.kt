package com.example.data.network

import com.example.data.model.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

data class ToolCall(
    val id: String,
    val name: String,
    val args: String
)

data class CompletionResult(
    val content: String,
    val toolCalls: List<ToolCall>,
    val totalTokens: Long?,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val finishReason: String?
)

class OpenAiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var noStreamOptions = false

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trimEnd('/') + "/models"
        val reqBuilder = Request.Builder().url(cleanUrl).get()
        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        val res = executeWithRetry(reqBuilder.build(), retries = 1)
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) {
            throw Exception("HTTP ${res.code}: ${extractErrorMsg(body)}")
        }

        val list = mutableListOf<String>()
        val json = JSONObject(body)
        val arr = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            if (item is JSONObject) {
                val id = item.optString("id").ifEmpty { item.optString("name") }
                if (id.isNotEmpty()) list.add(id)
            } else if (item is String) {
                list.add(item)
            }
        }
        list.sorted()
    }

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val cleanUrl = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            .put("max_tokens", 5)
            .put("stream", false)

        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        val res = executeWithRetry(reqBuilder.build(), retries = 0)
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) {
            throw Exception("HTTP ${res.code}: ${extractErrorMsg(body)}")
        }
        System.currentTimeMillis() - start
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        tools: JSONArray?,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean = true,
        onTextDelta: ((String) -> Unit)? = null
    ): CompletionResult = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trimEnd('/') + "/chat/completions"
        val bodyObj = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("stream", stream)

        if (maxTokens > 0) {
            bodyObj.put("max_tokens", maxTokens)
        }
        if (tools != null && tools.length() > 0) {
            bodyObj.put("tools", tools)
            bodyObj.put("tool_choice", "auto")
        }
        if (stream && !noStreamOptions) {
            bodyObj.put("stream_options", JSONObject().put("include_usage", true))
        }

        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        var res: Response
        try {
            res = executeWithRetry(reqBuilder.build(), retries = 2)
        } catch (e: Exception) {
            if (e.message?.contains("stream_options", ignoreCase = true) == true && !noStreamOptions) {
                noStreamOptions = true
                return@withContext chatCompletion(
                    baseUrl, apiKey, model, messages, tools, temperature, maxTokens, stream, onTextDelta
                )
            }
            throw e
        }

        val contentType = res.header("Content-Type", "")?.lowercase() ?: ""

        if (!res.isSuccessful) {
            val errBody = res.body?.string() ?: ""
            if (res.code == 400 && errBody.contains("stream_options", ignoreCase = true) && !noStreamOptions) {
                noStreamOptions = true
                return@withContext chatCompletion(
                    baseUrl, apiKey, model, messages, tools, temperature, maxTokens, stream, onTextDelta
                )
            }
            throw Exception("API Error (${res.code}): ${extractErrorMsg(errBody)}")
        }

        if (!contentType.contains("text/event-stream")) {
            // Non-streaming response
            val text = res.body?.string() ?: ""
            val json = JSONObject(text)
            val choices = json.optJSONArray("choices")
            val choice = choices?.optJSONObject(0)
            val message = choice?.optJSONObject("message")
            val content = message?.optString("content", "") ?: ""
            if (content.isNotEmpty()) {
                onTextDelta?.invoke(content)
            }

            val toolCalls = mutableListOf<ToolCall>()
            val rawTools = message?.optJSONArray("tool_calls")
            if (rawTools != null) {
                for (i in 0 until rawTools.length()) {
                    val tc = rawTools.optJSONObject(i) ?: continue
                    val id = tc.optString("id", "call_${System.currentTimeMillis()}_$i")
                    val fn = tc.optJSONObject("function")
                    val name = fn?.optString("name") ?: ""
                    val args = fn?.optString("arguments") ?: "{}"
                    toolCalls.add(ToolCall(id, name, args))
                }
            }

            val usage = json.optJSONObject("usage")
            val totalTokens = usage?.optLong("total_tokens")
            val promptTokens = usage?.optLong("prompt_tokens")
            val compTokens = usage?.optLong("completion_tokens")
            val finish = choice?.optString("finish_reason", "stop")

            return@withContext CompletionResult(
                content = content,
                toolCalls = toolCalls,
                totalTokens = totalTokens,
                promptTokens = promptTokens,
                completionTokens = compTokens,
                finishReason = finish
            )
        }

        // Streaming response (SSE)
        val inputStream = res.body?.byteStream() ?: throw Exception("Empty stream body")
        val reader = BufferedReader(InputStreamReader(inputStream))

        val contentBuilder = StringBuilder()
        val toolCallsMap = mutableMapOf<Int, MutableMap<String, String>>()
        var totalTokens: Long? = null
        var promptTokens: Long? = null
        var compTokens: Long? = null
        var finishReason: String? = null

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.substring(5).trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") {
                    finishReason = finishReason ?: "stop"
                    break
                }

                val chunk: JSONObject
                try {
                    chunk = JSONObject(data)
                } catch (e: Exception) {
                    continue
                }

                val usage = chunk.optJSONObject("usage")
                if (usage != null) {
                    totalTokens = usage.optLong("total_tokens")
                    promptTokens = usage.optLong("prompt_tokens")
                    compTokens = usage.optLong("completion_tokens")
                }

                val choices = chunk.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val ch = choices.optJSONObject(0)
                    val delta = ch?.optJSONObject("delta") ?: ch?.optJSONObject("message")
                    val text = delta?.optString("content", "") ?: ""
                    if (text.isNotEmpty()) {
                        contentBuilder.append(text)
                        onTextDelta?.invoke(text)
                    }

                    val tcArray = delta?.optJSONArray("tool_calls")
                    if (tcArray != null) {
                        for (i in 0 until tcArray.length()) {
                            val tc = tcArray.optJSONObject(i) ?: continue
                            val idx = if (tc.has("index")) tc.optInt("index") else i
                            val cur = toolCallsMap.getOrPut(idx) {
                                mutableMapOf("id" to "", "name" to "", "args" to "")
                            }
                            val id = tc.optString("id", "")
                            if (id.isNotEmpty() && cur["id"].isNullOrEmpty()) {
                                cur["id"] = id
                            }
                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                val name = fn.optString("name", "")
                                if (name.isNotEmpty()) {
                                    val curName = cur["name"] ?: ""
                                    cur["name"] = if (curName.isEmpty()) name else curName + name
                                }
                                val args = fn.optString("arguments", "")
                                if (args.isNotEmpty()) {
                                    cur["args"] = (cur["args"] ?: "") + args
                                }
                            }
                        }
                    }

                    val fr = ch?.optString("finish_reason", "")
                    if (!fr.isNullOrEmpty()) {
                        finishReason = fr
                    }
                }
            }
        } finally {
            reader.close()
        }

        val toolCalls = toolCallsMap.entries.sortedBy { it.key }.map { (idx, map) ->
            ToolCall(
                id = map["id"]?.ifEmpty { "call_${System.currentTimeMillis()}_$idx" } ?: "call_$idx",
                name = map["name"] ?: "",
                args = map["args"] ?: ""
            )
        }.filter { it.name.isNotEmpty() || it.args.isNotEmpty() }

        CompletionResult(
            content = contentBuilder.toString(),
            toolCalls = toolCalls,
            totalTokens = totalTokens,
            promptTokens = promptTokens,
            completionTokens = compTokens,
            finishReason = finishReason
        )
    }

    suspend fun searchTavily(
        query: String,
        apiKey: String,
        maxResults: Int = 5,
        searchDepth: String = "basic"
    ): List<WebSource> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw Exception("لم يتم إدخال مفتاح Tavily في الإعدادات")

        val payload = JSONObject()
            .put("api_key", apiKey.trim())
            .put("query", query.take(400))
            .put("search_depth", if (searchDepth == "advanced") "advanced" else "basic")
            .put("max_results", maxResults.coerceIn(1, 20))
            .put("include_answer", false)
            .put("include_raw_content", false)

        val req = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val res = executeWithRetry(req, retries = 1)
        val body = res.body?.string() ?: ""
        if (!res.isSuccessful) {
            throw Exception("Tavily Error (${res.code}): ${extractErrorMsg(body)}")
        }

        val json = JSONObject(body)
        val arr = json.optJSONArray("results") ?: JSONArray()
        val list = mutableListOf<WebSource>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val url = r.optString("url")
            if (url.isNotEmpty()) {
                val title = r.optString("title", url)
                val content = r.optString("content", "").take(1500)
                val score = if (r.has("score")) r.optDouble("score") else null
                list.add(WebSource(title, url, content, score))
            }
        }
        list
    }

    private suspend fun executeWithRetry(request: Request, retries: Int): Response {
        var attempt = 0
        while (true) {
            try {
                val response = client.newCall(request).execute()
                val code = response.code
                if (isRetryable(code) && attempt < retries) {
                    response.close()
                    attempt++
                    delay(900L * attempt)
                    continue
                }
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt < retries) {
                    attempt++
                    delay(900L * attempt)
                    continue
                }
                throw e
            }
        }
    }

    private fun isRetryable(code: Int): Boolean {
        return code in listOf(0, 408, 425, 429, 500, 502, 503, 504)
    }

    private fun extractErrorMsg(body: String): String {
        try {
            val j = JSONObject(body)
            val err = j.opt("error")
            if (err is JSONObject) {
                return err.optString("message").ifEmpty { err.toString() }
            } else if (err is String) {
                return err
            }
            val msg = j.optString("message")
            if (msg.isNotEmpty()) return msg
        } catch (_: Exception) {}
        return body.take(200)
    }
}
