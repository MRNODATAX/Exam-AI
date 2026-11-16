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

    private const val SYSTEM_PROMPT =
        "Kamu adalah asisten pengisi form. Analisis teks layar berikut, temukan pertanyaan dan opsi jawabannya. " +
            "Balas HANYA dengan teks persis dari satu pilihan jawaban yang paling benar tanpa tanda kutip, " +
            "tanpa huruf opsi (A/B/C/D) jika tidak ada di teks asli, dan tanpa penjelasan tambahan."

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun askAi(context: Context, screenText: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = PrefsManager.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key belum diisi di pengaturan."))
        }

        val provider = PrefsManager.getProvider(context)
        val model = PrefsManager.getModel(context)

        return@withContext try {
            when (provider) {
                PrefsManager.PROVIDER_GEMINI -> callGemini(apiKey, model, screenText)
                PrefsManager.PROVIDER_OMNIROUTE,
                PrefsManager.PROVIDER_STANDARD -> {
                    val baseUrl = PrefsManager.getBaseUrl(context)
                    callOpenAiCompatible(baseUrl, apiKey, model, screenText)
                }
                else -> callGemini(apiKey, model, screenText)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGemini(apiKey: String, model: String, screenText: String): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", SYSTEM_PROMPT))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", screenText))
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
                return Result.failure(RuntimeException("Gemini HTTP ${response.code}: $bodyStr"))
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
            return Result.success(cleanAnswer(text))
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        screenText: String
    ): Result<String> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("Base URL belum diisi."))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", screenText))
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
                return Result.failure(RuntimeException("API HTTP ${response.code}: $bodyStr"))
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
            return Result.success(cleanAnswer(text))
        }
    }

    private fun cleanAnswer(raw: String): String {
        return raw.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }
}
