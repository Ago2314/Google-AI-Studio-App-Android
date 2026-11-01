package com.example

import com.example.data.model.ChatMessage
import com.example.data.model.MediaAttachment
import com.example.data.model.PromptItem
import com.example.ui.AIStudioUiState
import com.example.ui.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun defaultUiState_hasCorrectInitialValues() {
        val state = AIStudioUiState()
        assertEquals("https://aistudio.google.com", state.currentUrl)
        assertEquals("Google AI Studio", state.pageTitle)
        assertTrue(state.isLoading)
        assertFalse(state.isDesktopMode)
        assertFalse(state.isAmoledTheme)
        assertEquals(AppTab.WEB_STUDIO, state.activeTab)
        assertTrue(state.isOnline)
        assertEquals("", state.geminiApiKey)
        assertEquals("gemini-2.5-flash", state.selectedModel)
        assertTrue(state.pendingAttachments.isEmpty())
        assertNull(state.pendingPromptToInject)
    }

    @Test
    fun uiState_copyUpdatesCorrectly() {
        val attachment = MediaAttachment(
            uriString = "content://media/123",
            mimeType = "image/png",
            fileName = "diagram.png"
        )
        val state = AIStudioUiState()
        val updated = state.copy(
            currentUrl = "https://aistudio.google.com/prompts/new_chat",
            isDesktopMode = true,
            isAmoledTheme = true,
            activeTab = AppTab.NATIVE_PLAYGROUND,
            isLoading = false,
            geminiApiKey = "AIzaSyTestKey123",
            pendingAttachments = listOf(attachment),
            pendingPromptToInject = "Hello from Prompt Hub"
        )

        assertEquals("https://aistudio.google.com/prompts/new_chat", updated.currentUrl)
        assertTrue(updated.isDesktopMode)
        assertTrue(updated.isAmoledTheme)
        assertEquals(AppTab.NATIVE_PLAYGROUND, updated.activeTab)
        assertFalse(updated.isLoading)
        assertEquals("AIzaSyTestKey123", updated.geminiApiKey)
        assertEquals(1, updated.pendingAttachments.size)
        assertEquals("Hello from Prompt Hub", updated.pendingPromptToInject)
    }

    @Test
    fun promptItem_createsWithDefaultsAndSync() {
        val prompt = PromptItem(
            title = "Test Prompt",
            content = "Write a kotlin test",
            isSyncedFromWeb = true
        )
        assertEquals("Test Prompt", prompt.title)
        assertEquals("Write a kotlin test", prompt.content)
        assertEquals("General", prompt.category)
        assertTrue(prompt.id.isNotBlank())
        assertTrue(prompt.isSyncedFromWeb)
    }

    @Test
    fun chatMessage_createsCorrectRolesAndAttachments() {
        val attachment = MediaAttachment(
            uriString = "content://docs/file.pdf",
            mimeType = "application/pdf",
            fileName = "spec.pdf"
        )
        val userMsg = ChatMessage(
            text = "Analyze this PDF",
            isUser = true,
            attachments = listOf(attachment)
        )
        val modelMsg = ChatMessage(text = "Summary of document", isUser = false)

        assertTrue(userMsg.isUser)
        assertFalse(modelMsg.isUser)
        assertFalse(userMsg.isError)
        assertEquals(1, userMsg.attachments.size)
        assertEquals("application/pdf", userMsg.attachments[0].mimeType)
    }

    @Test
    fun urlSanitization_handlesPrefixes() {
        val testUrl = "https://aistudio.google.com/live"
        val display = testUrl.removePrefix("https://").removePrefix("http://")
        assertEquals("aistudio.google.com/live", display)
    }
}
