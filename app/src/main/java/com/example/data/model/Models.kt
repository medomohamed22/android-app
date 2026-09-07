package com.example.data.model

data class WorkspaceFile(
    val type: String = "file", // "file" or "folder"
    val content: String = "",
    val mtime: Long = System.currentTimeMillis()
)

data class Attachment(
    val id: String,
    val name: String,
    val text: String
)

data class ActivityItem(
    val tool: String,
    val label: String,
    var detail: String,
    var status: String = "running" // "running", "done", "failed"
)

data class WebSource(
    val title: String,
    val url: String,
    val content: String = "",
    val score: Double? = null
)

data class Metrics(
    val model: String,
    val tokens: Long,
    val approx: Boolean = false,
    val requests: Int,
    val tavily: Int = 0,
    val tools: Int = 0,
    val elapsed: Long = 0L
)

data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    var content: String,
    val activity: MutableList<ActivityItem> = mutableListOf(),
    var isActivityOpen: Boolean = true,
    var sources: List<WebSource> = emptyList(),
    var metrics: Metrics? = null,
    var error: String? = null,
    var status: String = "done", // "running", "done", "failed", "aborted"
    val attachments: List<Attachment> = emptyList(),
    val ts: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String,
    var title: String,
    val messages: List<ChatMessage>,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

data class AppSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.2f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    val saveKey: Boolean = true,
    val models: List<String> = emptyList(),
    val tavilyKey: String = "",
    val tavilyMax: Int = 5,
    val tavilyDepth: String = "basic",
    val tavilyDefault: Boolean = false
)
