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

class OpenAiCompatibleProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val baseEndpoint: String = "https://api.openai.com/v1"
) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() && !baseEndpoint.contains("localhost") && !baseEndpoint.contains("127.0.0.1")) {
                return@withContext Result.failure(IllegalStateException("Missing API Key. Please configure it in AI Settings."))
            }

            try {
                val cleanBase = baseEndpoint.trimEnd('/')
                val url = if (cleanBase.endsWith("/chat/completions")) {
                    cleanBase
                } else if (cleanBase.endsWith("/v1")) {
                    "$cleanBase/chat/completions"
                } else {
                    "$cleanBase/v1/chat/completions"
                }

                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.3)

                    val messagesArray = JSONArray()

                    if (!systemPrompt.isNullOrBlank()) {
                        val sysMsg = JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                        messagesArray.put(sysMsg)
                    }

                    val userMsg = JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                    messagesArray.put(userMsg)

                    put("messages", messagesArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)

                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("HTTP ${response.code}: ${parseErrorMessage(responseStr)}")
                        )
                    }

                    val respJson = JSONObject(responseStr)
                    val choices = respJson.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.optJSONObject("message")
                        val content = message?.optString("content", "") ?: ""
                        return@withContext Result.success(content.trim())
                    }

                    Result.failure(IOException("No text content returned from chat completion."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        // Fast test with a 1-token prompt
        try {
            val result = generateText("ping", "Answer with 'pong' only.")
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: IOException("Connection test failed"))
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
