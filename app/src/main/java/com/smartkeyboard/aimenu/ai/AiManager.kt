package com.smartkeyboard.aimenu.ai

import android.content.Context
import com.smartkeyboard.aimenu.data.AiAction
import com.smartkeyboard.aimenu.data.AiProviderType
import com.smartkeyboard.aimenu.data.ConnectionStatus
import com.smartkeyboard.aimenu.data.KeyboardPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = KeyboardPreferences(context)

    var currentStatus: ConnectionStatus = ConnectionStatus.CHECKING
        private set

    var onStatusChanged: ((ConnectionStatus) -> Unit)? = null

    init {
        // Initial connection test
        verifyConnection()
    }

    fun getActiveProvider(): AiProvider {
        val apiKey = prefs.apiKey
        val model = prefs.aiModel
        val endpoint = prefs.aiEndpoint

        return when (prefs.aiProvider) {
            AiProviderType.GEMINI -> GeminiProvider(apiKey, model, endpoint)
            AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(apiKey, model, endpoint)
        }
    }

    /**
     * Conducts a real network connection test to verify credentials.
     * Never sets status to CONNECTED unless the endpoint actually responds successfully!
     */
    fun verifyConnection(callback: ((ConnectionStatus) -> Unit)? = null) {
        if (!prefs.aiEnabled) {
            currentStatus = ConnectionStatus.DISABLED
            onStatusChanged?.invoke(currentStatus)
            callback?.invoke(currentStatus)
            return
        }

        if (prefs.apiKey.isBlank()) {
            currentStatus = ConnectionStatus.MISSING_KEY
            onStatusChanged?.invoke(currentStatus)
            callback?.invoke(currentStatus)
            return
        }

        currentStatus = ConnectionStatus.CHECKING
        onStatusChanged?.invoke(currentStatus)

        scope.launch {
            val provider = getActiveProvider()
            val result = provider.testConnection()

            currentStatus = if (result.isSuccess) {
                ConnectionStatus.CONNECTED
            } else {
                val err = result.exceptionOrNull()?.message?.lowercase() ?: ""
                when {
                    err.contains("network") || err.contains("unable to resolve host") -> ConnectionStatus.NETWORK_ERROR
                    err.contains("401") || err.contains("key") || err.contains("auth") -> ConnectionStatus.MISSING_KEY
                    else -> ConnectionStatus.SERVER_ERROR
                }
            }

            withContext(Dispatchers.Main) {
                onStatusChanged?.invoke(currentStatus)
                callback?.invoke(currentStatus)
            }
        }
    }

    /**
     * Safely executes an AI action.
     * Guaranteed never to send sensitive/password fields!
     */
    fun executeAction(
        action: AiAction,
        inputText: String,
        targetLanguage: String = "English",
        codeLanguage: String = "Kotlin",
        isSensitiveField: Boolean,
        onComplete: (Result<String>) -> Unit
    ) {
        // 1. Strict security check: password / sensitive field protection
        if (isSensitiveField) {
            onComplete(Result.failure(SecurityException("AI assistance is strictly disabled in password and sensitive fields.")))
            return
        }

        if (!prefs.aiEnabled) {
            onComplete(Result.failure(IllegalStateException("AI features are disabled in settings.")))
            return
        }

        if (inputText.isBlank() && action != AiAction.ASK) {
            onComplete(Result.failure(IllegalArgumentException("Please enter or select text first.")))
            return
        }

        val systemPrompt = buildSystemPrompt(action, targetLanguage, codeLanguage)
        val provider = getActiveProvider()

        scope.launch {
            val result = provider.generateText(inputText, systemPrompt)
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    private fun buildSystemPrompt(action: AiAction, targetLanguage: String, codeLanguage: String): String {
        return when (action) {
            AiAction.CORRECT -> "You are a writing assistant. Fix grammar, punctuation, and typos in the following text. Preserve original meaning and tone. Return only the corrected text."
            AiAction.REWRITE -> "You are a professional editor. Rewrite and polish the following text to make it clear, concise, and engaging. Return only the rewritten text."
            AiAction.TRANSLATE -> "You are an expert translator. Translate the following text faithfully into $targetLanguage. Return only the translated text."
            AiAction.SUMMARIZE -> "You are a concise summarizer. Provide a crisp, clear summary of the core points in the following text."
            AiAction.EXPLAIN -> "Explain the key concepts of the provided text in simple, easy-to-understand terms."
            AiAction.FIX_CODE -> "You are a senior software engineer. Fix bugs and syntax issues in this $codeLanguage code. Return the corrected code followed by a 1-sentence note of what was fixed."
            AiAction.COMPLETE_CODE -> "You are an expert $codeLanguage programmer. Complete this code snippet logically and idiomatically."
            AiAction.GENERATE_TESTS -> "Write robust unit tests for the following $codeLanguage code snippet."
            AiAction.ASK -> "You are Smart Keyboard AI Assistant. Provide helpful, accurate, and concise answers."
        }
    }
}
