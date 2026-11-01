package com.example.data

import com.example.data.model.ChatMessage
import com.example.data.model.MediaAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiNativeClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(
        apiKey: String,
        prompt: String,
        systemInstruction: String? = null,
        history: List<ChatMessage> = emptyList(),
        attachments: List<MediaAttachment> = emptyList(),
        model: String = "gemini-2.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("No Gemini API Key provided. Please add your API key in Settings or the key banner.")
            )
        }

        try {
            val rootJson = JSONObject()

            // Optional System instruction
            if (!systemInstruction.isNullOrBlank()) {
                val sysPart = JSONObject().put("text", systemInstruction)
                val sysParts = JSONArray().put(sysPart)
                rootJson.put("system_instruction", JSONObject().put("parts", sysParts))
            }

            // Chat contents
            val contentsArray = JSONArray()

            // Include last 8 conversational turns for context
            val contextHistory = history.takeLast(8)
            for (msg in contextHistory) {
                if (msg.isError) continue
                val role = if (msg.isUser) "user" else "model"
                val part = JSONObject().put("text", msg.text)
                val turn = JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(part))
                }
                contentsArray.put(turn)
            }

            // Current prompt turn (Multimodal: supports images, PDFs, audio + text)
            val partsArray = JSONArray()
            for (att in attachments) {
                if (!att.base64Data.isNullOrBlank()) {
                    val inlineDataObj = JSONObject().apply {
                        put("mime_type", att.mimeType)
                        put("data", att.base64Data)
                    }
                    partsArray.put(JSONObject().put("inline_data", inlineDataObj))
                }
            }

            val textContent = if (prompt.isNotBlank()) prompt else "Please analyze the attached file."
            partsArray.put(JSONObject().put("text", textContent))

            val currentTurn = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }
            contentsArray.put(currentTurn)

            rootJson.put("contents", contentsArray)

            // Target URL
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val body = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(responseBody).optJSONObject("error")
                        errObj?.optString("message") ?: "HTTP error ${response.code}"
                    } catch (e: Exception) {
                        "Request failed with HTTP status ${response.code}"
                    }
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        return@withContext Result.success(text)
                    }
                }

                Result.failure(IOException("Received empty response from Gemini model."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
