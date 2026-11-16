package com.example.examai.network

import android.content.Context
import com.example.examai.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiApiHelper {

    private const val FORM_SYSTEM_PROMPT =
        "Kamu adalah asisten pengisi form. Analisis teks layar berikut, temukan pertanyaan dan opsi jawabannya. " +
            "Balas HANYA dengan teks persis dari satu pilihan jawaban yang paling benar tanpa tanda kutip, " +
            "tanpa huruf opsi (A/B/C/D) jika tidak ada di teks asli, dan tanpa penjelasan tambahan."

    private const val CHAT_SYSTEM_PROMPT =
        "Kamu adalah ExamAI, asisten belajar pribadi yang ringkas, akurat, dan jelas dalam Bahasa Indonesia."

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun askAi(context: Context, screenText: String): Result<String> {
        return sendPrompt(context, FORM_SYSTEM_PROMPT, screenText, cleanQuotes = true)
    }

    suspend fun askChat(context: Context, message: String): Result<String> {
        return sendPrompt(context, CHAT_SYSTEM_PROMPT, message, cleanQuotes = false)
    }

    suspend fun testConnection(context: Context): Result<String> {
        return sendPrompt(
            context,
            "Balas hanya dengan kata: OK",
            "Tes koneksi API",
            cleanQuotes = true
        )
    }

    private suspend fun sendPrompt(
        context: Context,
        systemPrompt: String,
        userContent: String,
        cleanQuotes: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = PrefsManager.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key belum diisi di Pengaturan."))
        }

        val provider = PrefsManager.getProvider(context)
        val model = PrefsManager.getModel(context)

        return@withContext try {
            val res = when (provider) {
                PrefsManager.PROVIDER_GEMINI -> callGemini(apiKey, model, systemPrompt, userContent)
                PrefsManager.PROVIDER_OMNIROUTE,
                PrefsManager.PROVIDER_STANDARD -> {
                    val baseUrl = normalizeChatUrl(PrefsManager.getBaseUrl(context))
                    callOpenAiCompatible(baseUrl, apiKey, model, systemPrompt, userContent)
                }
                else -> callGemini(apiKey, model, systemPrompt, userContent)
            }
            if (cleanQuotes) res.map { cleanAnswer(it) } else res
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeChatUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else if (trimmed.endsWith("/v1")) {
            "$trimmed/chat/completions"
        } else {
            trimmed
        }
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String
    ): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", userContent))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return Result.failure(RuntimeException("Gemini HTTP ${response.code}: ${extractErrorMessage(bodyStr)}"))
            }

            val json = JSONObject(bodyStr)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(RuntimeException("Respons Gemini kosong."))
            }

            val text = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                return Result.failure(RuntimeException("Jawaban AI kosong."))
            }
            return Result.success(text)
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String
    ): Result<String> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Base URL belum diisi."))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userContent))
            })
        }

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return Result.failure(RuntimeException("API HTTP ${response.code}: ${extractErrorMessage(bodyStr)}"))
            }

            val json = JSONObject(bodyStr)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(RuntimeException("Respons API kosong."))
            }

            val text = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "")
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                return Result.failure(RuntimeException("Jawaban AI kosong."))
            }
            return Result.success(text)
        }
    }

    private fun extractErrorMessage(bodyStr: String): String {
        return try {
            val json = JSONObject(bodyStr)
            json.optJSONObject("error")?.optString("message")
                ?: bodyStr.take(140)
        } catch (_: Exception) {
            bodyStr.take(140)
        }
    }

    private fun cleanAnswer(raw: String): String {
        return raw.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }
}
