package com.smartkeyboard.aimenu.ai

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

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
    private val baseEndpoint: String = "https://generativelanguage.googleapis.com"
) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing API Key. Please configure it in AI Settings."))
            }

            try {
                val cleanBase = baseEndpoint.trimEnd('/')
                val url = "$cleanBase/v1beta/models/$model:generateContent?key=$apiKey"

                val jsonBody = JSONObject().apply {
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray()
                        val partObj = JSONObject().apply {
                            val combinedText = if (!systemPrompt.isNullOrBlank()) {
                                "Instructions: $systemPrompt\n\nInput: $prompt"
                            } else {
                                prompt
                            }
                            put("text", combinedText)
                        }
                        partsArray.put(partObj)
                        put("parts", partsArray)
                    }
                    contentsArray.put(contentObj)
                    put("contents", contentsArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("HTTP ${response.code}: ${parseErrorMessage(responseStr)}")
                        )
                    }

                    val respJson = JSONObject(responseStr)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text", "")
                            return@withContext Result.success(text.trim())
                        }
                    }

                    Result.failure(IOException("No text content returned from Gemini model."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API Key is empty."))
        }

        try {
            val cleanBase = baseEndpoint.trimEnd('/')
            // Fast ping using model metadata endpoint
            val url = "$cleanBase/v1beta/models/$model?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val err = response.body?.string() ?: ""
                    Result.failure(IOException("HTTP ${response.code}: ${parseErrorMessage(err)}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(rawJson: String): String {
        return try {
            val obj = JSONObject(rawJson)
            val errorObj = obj.optJSONObject("error")
            errorObj?.optString("message") ?: "Unknown error"
        } catch (_: Exception) {
            "Unable to parse response"
        }
    }
}
