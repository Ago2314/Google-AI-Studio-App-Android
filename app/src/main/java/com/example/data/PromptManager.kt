package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ChatMessage
import com.example.data.model.MediaAttachment
import com.example.data.model.PromptItem
import org.json.JSONArray
import org.json.JSONObject

class PromptManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_studio_local_data", Context.MODE_PRIVATE)

    fun getPrompts(): List<PromptItem> {
        val raw = prefs.getString(KEY_PROMPTS, null) ?: return getDefaultPrompts()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<PromptItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PromptItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        content = obj.optString("content"),
                        category = obj.optString("category", "General"),
                        systemInstruction = obj.optString("systemInstruction", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isSyncedFromWeb = obj.optBoolean("isSyncedFromWeb", false)
                    )
                )
            }
            if (list.isEmpty()) getDefaultPrompts() else list
        } catch (e: Exception) {
            getDefaultPrompts()
        }
    }

    fun savePrompt(prompt: PromptItem) {
        val current = getPrompts().toMutableList()
        val index = current.indexOfFirst { it.id == prompt.id }
        if (index >= 0) {
            current[index] = prompt
        } else {
            current.add(0, prompt)
        }
        persistPrompts(current)
    }

    fun mergeSyncedPrompts(titles: List<String>): Int {
        val current = getPrompts().toMutableList()
        var addedCount = 0
        for (rawTitle in titles) {
            val cleanTitle = rawTitle.trim()
            if (cleanTitle.isBlank() || cleanTitle.length < 3) continue
            val exists = current.any { it.title.equals(cleanTitle, ignoreCase = true) }
            if (!exists) {
                current.add(
                    0,
                    PromptItem(
                        title = cleanTitle,
                        content = "Continue prompt or test conversation for: $cleanTitle",
                        category = "Synced Web",
                        systemInstruction = "",
                        isSyncedFromWeb = true
                    )
                )
                addedCount++
            }
        }
        if (addedCount > 0) {
            persistPrompts(current)
        }
        return addedCount
    }

    fun deletePrompt(id: String) {
        val current = getPrompts().toMutableList()
        current.removeAll { it.id == id }
        persistPrompts(current)
    }

    private fun persistPrompts(list: List<PromptItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
                put("category", item.category)
                put("systemInstruction", item.systemInstruction)
                put("timestamp", item.timestamp)
                put("isSyncedFromWeb", item.isSyncedFromWeb)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROMPTS, array.toString()).apply()
    }

    fun getChatHistory(): List<ChatMessage> {
        val raw = prefs.getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val attachmentsList = mutableListOf<MediaAttachment>()
                val attArray = obj.optJSONArray("attachments")
                if (attArray != null) {
                    for (j in 0 until attArray.length()) {
                        val attObj = attArray.getJSONObject(j)
                        attachmentsList.add(
                            MediaAttachment(
                                uriString = attObj.optString("uriString"),
                                mimeType = attObj.optString("mimeType"),
                                fileName = attObj.optString("fileName")
                            )
                        )
                    }
                }

                list.add(
                    ChatMessage(
                        id = obj.optString("id"),
                        text = obj.optString("text"),
                        isUser = obj.optBoolean("isUser"),
                        timestamp = obj.optLong("timestamp"),
                        isError = obj.optBoolean("isError", false),
                        attachments = attachmentsList
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChatHistory(history: List<ChatMessage>) {
        val array = JSONArray()
        for (item in history.takeLast(60)) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("isUser", item.isUser)
                put("timestamp", item.timestamp)
                put("isError", item.isError)

                if (item.attachments.isNotEmpty()) {
                    val attArray = JSONArray()
                    for (att in item.attachments) {
                        attArray.put(
                            JSONObject().apply {
                                put("uriString", att.uriString)
                                put("mimeType", att.mimeType)
                                put("fileName", att.fileName)
                            }
                        )
                    }
                    put("attachments", attArray)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CHAT_HISTORY, array.toString()).apply()
    }

    fun clearChatHistory() {
        prefs.edit().remove(KEY_CHAT_HISTORY).apply()
    }

    private fun getDefaultPrompts(): List<PromptItem> {
        return listOf(
            PromptItem(
                id = "p1",
                title = "Code Reviewer & Optimizer",
                category = "Coding",
                systemInstruction = "You are a senior software engineer. Review code for bugs, security vulnerabilities, performance, and best practices.",
                content = "Please analyze this code, explain any edge cases or potential memory leaks, and provide a clean, modern refactoring."
            ),
            PromptItem(
                id = "p2",
                title = "Strict JSON Schema Generator",
                category = "Data",
                systemInstruction = "Output ONLY valid JSON without markdown fences, commentary, or extra whitespace.",
                content = "Extract the key entities and structured attributes from the following text into a clean JSON object."
            ),
            PromptItem(
                id = "p3",
                title = "System Architecture Advisor",
                category = "Architecture",
                systemInstruction = "You are a cloud architect specializing in low-latency mobile and serverless systems.",
                content = "Design a scalable architecture for an Android app needing offline-first sync, minimal battery footprint, and secure credential storage."
            ),
            PromptItem(
                id = "p4",
                title = "Bug & Exception Explainer",
                category = "Debugging",
                systemInstruction = "Explain stack traces and errors clearly, identify the root cause, and give step-by-step fix commands.",
                content = "Here is the error log / stack trace. What caused it and how do I fix it cleanly?"
            )
        )
    }

    companion object {
        private const val KEY_PROMPTS = "saved_prompts_json"
        private const val KEY_CHAT_HISTORY = "saved_chat_history_json"
    }
}
