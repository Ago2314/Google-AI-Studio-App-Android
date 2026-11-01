package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages sensitive user settings such as the Gemini API Key using EncryptedSharedPreferences.
 */
class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to private prefs", e)
        context.applicationContext.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun isDesktopMode(): Boolean {
        return prefs.getBoolean(KEY_DESKTOP_MODE, false)
    }

    fun setDesktopMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    fun getStartUrl(): String {
        return prefs.getString(KEY_START_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setStartUrl(url: String) {
        prefs.edit().putString(KEY_START_URL, url.ifBlank { DEFAULT_URL }).apply()
    }

    fun isAmoledTheme(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_THEME, false)
    }

    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_THEME, enabled).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    companion object {
        private const val TAG = "SecurePreferences"
        private const val PREFS_FILE_NAME = "ai_studio_secure_prefs"
        private const val FALLBACK_PREFS_FILE = "ai_studio_prefs_fallback"

        const val DEFAULT_URL = "https://aistudio.google.com"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_DESKTOP_MODE = "desktop_mode_enabled"
        private const val KEY_START_URL = "start_url"
        private const val KEY_AMOLED_THEME = "amoled_theme_enabled"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
    }
}
