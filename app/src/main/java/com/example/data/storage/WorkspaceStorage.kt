package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import com.example.data.workspace.WorkspaceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WorkspaceStorage(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aiway_prefs", Context.MODE_PRIVATE)

    fun saveSettings(settings: AppSettings) {
        val editor = prefs.edit()
        editor.putString("baseUrl", settings.baseUrl)
        editor.putString("model", settings.model)
        editor.putFloat("temperature", settings.temperature)
        editor.putInt("maxTokens", settings.maxTokens)
        editor.putString("systemPrompt", settings.systemPrompt)
        editor.putBoolean("saveKey", settings.saveKey)
        editor.putInt("tavilyMax", settings.tavilyMax)
        editor.putString("tavilyDepth", settings.tavilyDepth)
        editor.putBoolean("tavilyDefault", settings.tavilyDefault)

        val modelsArr = JSONArray()
        settings.models.forEach { modelsArr.put(it) }
        editor.putString("modelsJson", modelsArr.toString())

        if (settings.saveKey) {
            editor.putString("apiKey", settings.apiKey)
            editor.putString("tavilyKey", settings.tavilyKey)
        } else {
            editor.remove("apiKey")
            editor.remove("tavilyKey")
        }
        editor.apply()
    }

    fun loadSettings(): AppSettings {
        val saveKey = prefs.getBoolean("saveKey", true)
        val modelsList = mutableListOf<String>()
        val modelsStr = prefs.getString("modelsJson", null)
        if (modelsStr != null) {
            try {
                val arr = JSONArray(modelsStr)
                for (i in 0 until arr.length()) {
                    modelsList.add(arr.getString(i))
                }
            } catch (_: Exception) {}
        }

        return AppSettings(
            baseUrl = prefs.getString("baseUrl", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = if (saveKey) (prefs.getString("apiKey", "") ?: "") else "",
            model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini",
            temperature = prefs.getFloat("temperature", 0.2f),
            maxTokens = prefs.getInt("maxTokens", 4096),
            systemPrompt = prefs.getString("systemPrompt", "") ?: "",
            saveKey = saveKey,
            models = modelsList,
            tavilyKey = if (saveKey) (prefs.getString("tavilyKey", "") ?: "") else "",
            tavilyMax = prefs.getInt("tavilyMax", 5),
            tavilyDepth = prefs.getString("tavilyDepth", "basic") ?: "basic",
            tavilyDefault = prefs.getBoolean("tavilyDefault", false)
        )
    }

    fun saveWorkspace(manager: WorkspaceManager) {
        try {
            val root = JSONObject()
            val filesObj = JSONObject()
            manager.sortedFiles().forEach { (p, f) ->
                val fo = JSONObject()
                fo.put("type", f.type)
                fo.put("content", f.content)
                fo.put("mtime", f.mtime)
                filesObj.put(p, fo)
            }
            root.put("files", filesObj)

            val tabsArr = JSONArray()
            manager.openTabs.forEach { tabsArr.put(it) }
            root.put("openTabs", tabsArr)
            root.put("activePath", manager.activePath ?: JSONObject.NULL)

            val file = File(context.filesDir, "workspace.json")
            file.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadWorkspace(manager: WorkspaceManager): Boolean {
        try {
            val file = File(context.filesDir, "workspace.json")
            if (!file.exists()) return false
            val text = file.readText()
            val root = JSONObject(text)
            val filesObj = root.optJSONObject("files") ?: return false

            manager.files.clear()
            val keys = filesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val fo = filesObj.optJSONObject(key) ?: continue
                val type = fo.optString("type", "file")
                val content = fo.optString("content", "")
                val mtime = fo.optLong("mtime", System.currentTimeMillis())
                manager.files[key] = WorkspaceFile(type = type, content = content, mtime = mtime)
            }

            manager.openTabs.clear()
            val tabsArr = root.optJSONArray("openTabs")
            if (tabsArr != null) {
                for (i in 0 until tabsArr.length()) {
                    val t = tabsArr.getString(i)
                    if (manager.files[t]?.type == "file") {
                        manager.openTabs.add(t)
                    }
                }
            }

            val act = root.optString("activePath", "")
            manager.activePath = if (act.isNotEmpty() && manager.files[act]?.type == "file") {
                act
            } else {
                manager.openTabs.firstOrNull()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun saveChats(chats: List<ChatSession>) {
        try {
            val arr = JSONArray()
            chats.forEach { session ->
                val sObj = JSONObject()
                sObj.put("id", session.id)
                sObj.put("title", session.title)
                sObj.put("createdAt", session.createdAt)
                sObj.put("updatedAt", session.updatedAt)

                val msgsArr = JSONArray()
                session.messages.forEach { msg ->
                    val mObj = JSONObject()
                    mObj.put("id", msg.id)
                    mObj.put("role", msg.role)
                    mObj.put("content", msg.content)
                    mObj.put("status", if (msg.status == "running") "failed" else msg.status)
                    mObj.put("ts", msg.ts)
                    if (msg.error != null) mObj.put("error", msg.error)

                    val actArr = JSONArray()
                    msg.activity.forEach { act ->
                        val aObj = JSONObject()
                        aObj.put("tool", act.tool)
                        aObj.put("label", act.label)
                        aObj.put("detail", act.detail)
                        aObj.put("status", if (act.status == "running") "failed" else act.status)
                        actArr.put(aObj)
                    }
                    mObj.put("activity", actArr)

                    val srcArr = JSONArray()
                    msg.sources.forEach { src ->
                        val srcObj = JSONObject()
                        srcObj.put("title", src.title)
                        srcObj.put("url", src.url)
                        srcObj.put("content", src.content)
                        if (src.score != null) srcObj.put("score", src.score)
                        srcArr.put(srcObj)
                    }
                    mObj.put("sources", srcArr)

                    if (msg.metrics != null) {
                        val mt = msg.metrics!!
                        val mtObj = JSONObject()
                        mtObj.put("model", mt.model)
                        mtObj.put("tokens", mt.tokens)
                        mtObj.put("approx", mt.approx)
                        mtObj.put("requests", mt.requests)
                        mtObj.put("tavily", mt.tavily)
                        mtObj.put("tools", mt.tools)
                        mtObj.put("elapsed", mt.elapsed)
                        mObj.put("metrics", mtObj)
                    }

                    val attArr = JSONArray()
                    msg.attachments.forEach { att ->
                        val attObj = JSONObject()
                        attObj.put("id", att.id)
                        attObj.put("name", att.name)
                        attObj.put("text", att.text)
                        attArr.put(attObj)
                    }
                    mObj.put("attachments", attArr)

                    msgsArr.put(mObj)
                }
                sObj.put("messages", msgsArr)
                arr.put(sObj)
            }

            val file = File(context.filesDir, "chats.json")
            file.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadChats(): List<ChatSession> {
        val list = mutableListOf<ChatSession>()
        try {
            val file = File(context.filesDir, "chats.json")
            if (!file.exists()) return emptyList()
            val text = file.readText()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val sObj = arr.optJSONObject(i) ?: continue
                val id = sObj.optString("id")
                val title = sObj.optString("title", "محادثة")
                val createdAt = sObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = sObj.optLong("updatedAt", createdAt)

                val msgsList = mutableListOf<ChatMessage>()
                val msgsArr = sObj.optJSONArray("messages")
                if (msgsArr != null) {
                    for (j in 0 until msgsArr.length()) {
                        val mObj = msgsArr.optJSONObject(j) ?: continue
                        val mId = mObj.optString("id")
                        val role = mObj.optString("role")
                        val content = mObj.optString("content", "")
                        val status = mObj.optString("status", "done")
                        val ts = mObj.optLong("ts", System.currentTimeMillis())
                        val err = if (mObj.has("error")) mObj.optString("error") else null

                        val acts = mutableListOf<ActivityItem>()
                        val actArr = mObj.optJSONArray("activity")
                        if (actArr != null) {
                            for (k in 0 until actArr.length()) {
                                val aObj = actArr.optJSONObject(k) ?: continue
                                acts.add(
                                    ActivityItem(
                                        tool = aObj.optString("tool"),
                                        label = aObj.optString("label"),
                                        detail = aObj.optString("detail"),
                                        status = aObj.optString("status")
                                    )
                                )
                            }
                        }

                        val srcs = mutableListOf<WebSource>()
                        val srcArr = mObj.optJSONArray("sources")
                        if (srcArr != null) {
                            for (k in 0 until srcArr.length()) {
                                val so = srcArr.optJSONObject(k) ?: continue
                                srcs.add(
                                    WebSource(
                                        title = so.optString("title"),
                                        url = so.optString("url"),
                                        content = so.optString("content"),
                                        score = if (so.has("score")) so.optDouble("score") else null
                                    )
                                )
                            }
                        }

                        var mt: Metrics? = null
                        val mtObj = mObj.optJSONObject("metrics")
                        if (mtObj != null) {
                            mt = Metrics(
                                model = mtObj.optString("model"),
                                tokens = mtObj.optLong("tokens"),
                                approx = mtObj.optBoolean("approx"),
                                requests = mtObj.optInt("requests"),
                                tavily = mtObj.optInt("tavily"),
                                tools = mtObj.optInt("tools"),
                                elapsed = mtObj.optLong("elapsed")
                            )
                        }

                        val atts = mutableListOf<Attachment>()
                        val attArr = mObj.optJSONArray("attachments")
                        if (attArr != null) {
                            for (k in 0 until attArr.length()) {
                                val ao = attArr.optJSONObject(k) ?: continue
                                atts.add(
                                    Attachment(
                                        id = ao.optString("id"),
                                        name = ao.optString("name"),
                                        text = ao.optString("text")
                                    )
                                )
                            }
                        }

                        msgsList.add(
                            ChatMessage(
                                id = mId,
                                role = role,
                                content = content,
                                activity = acts,
                                isActivityOpen = false,
                                sources = srcs,
                                metrics = mt,
                                error = err,
                                status = status,
                                attachments = atts,
                                ts = ts
                            )
                        )
                    }
                }

                list.add(
                    ChatSession(
                        id = id,
                        title = title,
                        messages = msgsList,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.updatedAt }
    }
}
