package com.example.data.model

import java.util.UUID

data class MediaAttachment(
    val uriString: String,
    val mimeType: String,
    val fileName: String,
    val base64Data: String? = null
)

data class PromptItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val category: String = "General",
    val systemInstruction: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSyncedFromWeb: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val attachments: List<MediaAttachment> = emptyList()
)
