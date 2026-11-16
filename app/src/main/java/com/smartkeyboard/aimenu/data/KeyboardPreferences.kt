package com.smartkeyboard.aimenu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class KeyboardPreferences(private val context: Context) {

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for environments where Keystore AES-GCM is restricted or uninitialized
            context.getSharedPreferences("${SECURE_PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    private val normalPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(NORMAL_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Appearance & Layout
    var theme: KeyboardThemeType
        get() = try {
            KeyboardThemeType.valueOf(normalPrefs.getString(KEY_THEME, KeyboardThemeType.DARK_GLASS.name)!!)
        } catch (_: Exception) {
            KeyboardThemeType.DARK_GLASS
        }
        set(value) = normalPrefs.edit().putString(KEY_THEME, value.name).apply()

    var keyboardHeightPercent: Int
        get() = normalPrefs.getInt(KEY_HEIGHT, 100) // 80% to 120%
        set(value) = normalPrefs.edit().putInt(KEY_HEIGHT, value).apply()

    var vibrationEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_VIBRATION, true)
        set(value) = normalPrefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    var soundEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_SOUND, false)
        set(value) = normalPrefs.edit().putBoolean(KEY_SOUND, value).apply()

    // Smart Typing
    var suggestionsEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = normalPrefs.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var autocorrectEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_AUTOCORRECT, true)
        set(value) = normalPrefs.edit().putBoolean(KEY_AUTOCORRECT, value).apply()

    var autoCapitalizeEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_AUTO_CAPS, true)
        set(value) = normalPrefs.edit().putBoolean(KEY_AUTO_CAPS, value).apply()

    var defaultLanguage: String
        get() = normalPrefs.getString(KEY_DEFAULT_LANG, "en") ?: "en"
        set(value) = normalPrefs.edit().putString(KEY_DEFAULT_LANG, value).apply()

    // AI Integration
    var aiEnabled: Boolean
        get() = normalPrefs.getBoolean(KEY_AI_ENABLED, true)
        set(value) = normalPrefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    var aiProvider: AiProviderType
        get() = try {
            AiProviderType.valueOf(normalPrefs.getString(KEY_AI_PROVIDER, AiProviderType.GEMINI.name)!!)
        } catch (_: Exception) {
            AiProviderType.GEMINI
        }
        set(value) = normalPrefs.edit().putString(KEY_AI_PROVIDER, value.name).apply()

    var aiModel: String
        get() {
            val def = if (aiProvider == AiProviderType.GEMINI) "gemini-1.5-flash" else "gpt-4o-mini"
            return normalPrefs.getString(KEY_AI_MODEL, def) ?: def
        }
        set(value) = normalPrefs.edit().putString(KEY_AI_MODEL, value).apply()

    var aiEndpoint: String
        get() {
            val def = if (aiProvider == AiProviderType.GEMINI) {
                "https://generativelanguage.googleapis.com"
            } else {
                "https://api.openai.com/v1"
            }
            return normalPrefs.getString(KEY_AI_ENDPOINT, def) ?: def
        }
        set(value) = normalPrefs.edit().putString(KEY_AI_ENDPOINT, value).apply()

    // Secure Credentials Storage
    var apiKey: String
        get() = securePrefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_API_KEY, value).apply()

    fun clearCredentials() {
        securePrefs.edit().clear().apply()
    }

    // MCP Servers Storage
    fun getMcpServers(): List<McpServerConfig> {
        val jsonStr = normalPrefs.getString(KEY_MCP_SERVERS, "[]") ?: "[]"
        val list = mutableListOf<McpServerConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val toolsArray = obj.optJSONArray("tools") ?: JSONArray()
                val tools = mutableListOf<String>()
                for (j in 0 until toolsArray.length()) {
                    tools.add(toolsArray.getString(j))
                }
                list.add(
                    McpServerConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        endpointUrl = obj.getString("endpointUrl"),
                        authHeader = obj.optString("authHeader", ""),
                        transport = obj.optString("transport", "SSE"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        availableTools = tools
                    )
                )
            }
        } catch (_: Exception) {
            // Return empty list on parse error
        }
        return list
    }

    fun saveMcpServers(servers: List<McpServerConfig>) {
        val array = JSONArray()
        for (server in servers) {
            val obj = JSONObject()
            obj.put("id", server.id)
            obj.put("name", server.name)
            obj.put("endpointUrl", server.endpointUrl)
            obj.put("authHeader", server.authHeader)
            obj.put("transport", server.transport)
            obj.put("isEnabled", server.isEnabled)
            val toolsArray = JSONArray()
            server.availableTools.forEach { toolsArray.put(it) }
            obj.put("tools", toolsArray)
            array.put(obj)
        }
        normalPrefs.edit().putString(KEY_MCP_SERVERS, array.toString()).apply()
    }

    companion object {
        private const val NORMAL_PREFS_NAME = "smart_kb_preferences"
        private const val SECURE_PREFS_NAME = "smart_kb_secure_keys"

        private const val KEY_THEME = "pref_theme"
        private const val KEY_HEIGHT = "pref_height_percent"
        private const val KEY_VIBRATION = "pref_vibration"
        private const val KEY_SOUND = "pref_sound"
        private const val KEY_SUGGESTIONS = "pref_suggestions"
        private const val KEY_AUTOCORRECT = "pref_autocorrect"
        private const val KEY_AUTO_CAPS = "pref_auto_caps"
        private const val KEY_DEFAULT_LANG = "pref_default_lang"

        private const val KEY_AI_ENABLED = "pref_ai_enabled"
        private const val KEY_AI_PROVIDER = "pref_ai_provider"
        private const val KEY_AI_MODEL = "pref_ai_model"
        private const val KEY_AI_ENDPOINT = "pref_ai_endpoint"
        private const val KEY_API_KEY = "pref_secure_api_key"
        private const val KEY_MCP_SERVERS = "pref_mcp_servers"
    }
}
