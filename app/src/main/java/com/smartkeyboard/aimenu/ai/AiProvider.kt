package com.smartkeyboard.aimenu.ai

/**
 * Common interface for AI Provider adapters.
 */
interface AiProvider {
    /**
     * Send prompt and receive generated text.
     */
    suspend fun generateText(prompt: String, systemPrompt: String? = null): Result<String>

    /**
     * Test connection to provider endpoint and credentials.
     * Returns true if verified, or Result.failure with descriptive error.
     */
    suspend fun testConnection(): Result<Boolean>
}
