package com.smartkeyboard.aimenu.data

import androidx.annotation.ColorRes
import com.smartkeyboard.aimenu.R

/**
 * Status of the AI provider or MCP server connection.
 */
enum class ConnectionStatus(
    val description: String,
    @ColorRes val colorRes: Int
) {
    CONNECTED("Connected", R.color.status_green),
    CHECKING("Checking connection…", R.color.status_amber),
    DISCONNECTED("Disconnected / Misconfigured", R.color.status_red),
    MISSING_KEY("Missing API key", R.color.status_red),
    NETWORK_ERROR("Network unavailable", R.color.status_red),
    SERVER_ERROR("Server unavailable / HTTP Error", R.color.status_red),
    DISABLED("AI disabled in settings", R.color.status_disabled)
}

/**
 * Supported AI Provider types.
 */
enum class AiProviderType(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI_COMPATIBLE("OpenAI-Compatible (DeepSeek, Groq, Ollama, OpenAI)")
}

/**
 * User-triggered AI Actions.
 */
enum class AiAction(val actionName: String) {
    CORRECT("Correct Grammar"),
    REWRITE("Rewrite & Polish"),
    TRANSLATE("Translate"),
    SUMMARIZE("Summarize"),
    EXPLAIN("Explain"),
    FIX_CODE("Fix Code"),
    COMPLETE_CODE("Complete Code"),
    GENERATE_TESTS("Generate Unit Tests"),
    ASK("Ask Assistant")
}

/**
 * MCP Server configuration for user-supplied Model Context Protocol servers.
 */
data class McpServerConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val authHeader: String = "",
    val transport: String = "SSE", // SSE or JSON-RPC
    val isEnabled: Boolean = true,
    val availableTools: List<String> = emptyList()
)

/**
 * Keyboard theme styles.
 */
enum class KeyboardThemeType(val displayName: String) {
    DARK_GLASS("Dark Glass"),
    LIGHT_GLASS("Light Glass"),
    OLED_GLASS("OLED Black Glass")
}

/**
 * Active keyboard layout mode.
 */
enum class KeyboardMode {
    ENGLISH,
    ARABIC,
    SYMBOLS
}

/**
 * Shift / Caps-lock state.
 */
enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
}

/**
 * Model representing a single key on the keyboard surface.
 */
data class KeyModel(
    val primaryLabel: String,
    val subLabel: String? = null,
    val code: Int = 0,
    val isSpecial: Boolean = false,
    val widthWeight: Float = 1.0f,
    val actionType: KeyActionType = KeyActionType.CHARACTER
)

enum class KeyActionType {
    CHARACTER,
    BACKSPACE,
    SHIFT,
    SPACE,
    ENTER,
    SWITCH_MODE,
    SWITCH_LANGUAGE,
    CODE_PANEL,
    VOICE_TYPING,
    SETTINGS
}
