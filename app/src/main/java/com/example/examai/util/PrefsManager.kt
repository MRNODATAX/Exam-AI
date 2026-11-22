package com.example.examai.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AnswerHistoryItem(
    val timestamp: String,
    val question: String,
    val answer: String
)

object PrefsManager {
    private const val PREFS_NAME = "exam_ai_prefs"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_OPACITY = "floating_opacity"
    private const val KEY_HISTORY = "answer_history"
    private const val KEY_USER_NAME = "user_name"

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

    fun saveOpacity(context: Context, opacityPercent: Int) {
        getPrefs(context).edit().putInt(KEY_OPACITY, opacityPercent.coerceIn(30, 100)).apply()
    }

    fun getOpacity(context: Context): Int {
        return getPrefs(context).getInt(KEY_OPACITY, 100).coerceIn(30, 100)
    }

    fun addHistory(context: Context, questionSnippet: String, answer: String) {
        val raw = getPrefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val item = JSONObject().apply {
            put("timestamp", time)
            put("question", questionSnippet.take(120))
            put("answer", answer)
        }
        val updated = JSONArray().apply {
            put(item)
            for (i in 0 until minOf(arr.length(), 29)) {
                put(arr.get(i))
            }
        }
        getPrefs(context).edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    fun getHistory(context: Context): List<AnswerHistoryItem> {
        val raw = getPrefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<AnswerHistoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AnswerHistoryItem(
                        timestamp = obj.optString("timestamp", ""),
                        question = obj.optString("question", ""),
                        answer = obj.optString("answer", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }

    fun saveUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "") ?: ""
    }
}
