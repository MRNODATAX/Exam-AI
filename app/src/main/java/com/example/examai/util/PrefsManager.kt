package com.example.examai.util

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "exam_ai_prefs"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
    private const val KEY_BASE_URL = "ai_base_url"

    const val PROVIDER_GEMINI = "GEMINI"
    const val PROVIDER_OMNIROUTE = "OMNIROUTE"
    const val PROVIDER_STANDARD = "STANDARD"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(
        context: Context,
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_BASE_URL, baseUrl.trim())
            .apply()
    }

    fun getProvider(context: Context): String {
        return getPrefs(context).getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun getModel(context: Context): String {
        val saved = getPrefs(context).getString(KEY_MODEL, "") ?: ""
        if (saved.isNotEmpty()) return saved
        return when (getProvider(context)) {
            PROVIDER_GEMINI -> "gemini-2.5-flash"
            PROVIDER_OMNIROUTE -> "gpt-4o-mini"
            else -> "gpt-4o-mini"
        }
    }

    fun getBaseUrl(context: Context): String {
        val saved = getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
        if (saved.isNotEmpty()) return saved
        return when (getProvider(context)) {
            PROVIDER_OMNIROUTE -> "https://api.omniroute.ai/v1/chat/completions"
            PROVIDER_STANDARD -> "https://api.openai.com/v1/chat/completions"
            else -> ""
        }
    }
}
