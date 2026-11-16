package com.smartkeyboard.aimenu

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartkeyboard.aimenu.ai.McpClient
import com.smartkeyboard.aimenu.data.KeyboardPreferences
import com.smartkeyboard.aimenu.data.McpServerConfig
import kotlinx.coroutines.launch
import java.util.UUID

class McpSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var etMcpName: EditText
    private lateinit var etMcpUrl: EditText
    private lateinit var etMcpAuth: EditText
    private lateinit var btnSaveMcpServer: Button
    private lateinit var containerMcpServers: LinearLayout

    private val configuredServers = mutableListOf<McpServerConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_settings)

        prefs = KeyboardPreferences(this)
        etMcpName = findViewById(R.id.etMcpName)
        etMcpUrl = findViewById(R.id.etMcpUrl)
        etMcpAuth = findViewById(R.id.etMcpAuth)
        btnSaveMcpServer = findViewById(R.id.btnSaveMcpServer)
        containerMcpServers = findViewById(R.id.containerMcpServers)

        loadServers()

        btnSaveMcpServer.setOnClickListener {
            val name = etMcpName.text.toString().trim()
            val url = etMcpUrl.text.toString().trim()
            val auth = etMcpAuth.text.toString().trim()

            if (name.isBlank() || url.isBlank()) {
                Toast.makeText(this, "Please provide both server name and endpoint URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newServer = McpServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                endpointUrl = url,
                authHeader = auth,
                transport = "SSE",
                isEnabled = true,
                availableTools = listOf("read_resource", "query_context")
            )

            configuredServers.add(newServer)
            prefs.saveMcpServers(configuredServers)
            renderServersList()

            etMcpName.setText("")
            etMcpUrl.setText("")
            etMcpAuth.setText("")
            Toast.makeText(this, "MCP Server saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadServers() {
        configuredServers.clear()
        configuredServers.addAll(prefs.getMcpServers())
        renderServersList()
    }

    private fun renderServersList() {
        containerMcpServers.removeAllViews()

        if (configuredServers.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No MCP servers configured yet. Add your endpoint above."
                setTextColor(getColor(R.color.glass_key_subtext_dark))
                textSize = 13f
                setPadding(0, 16, 0, 16)
            }
            containerMcpServers.addView(emptyTv)
            return
        }

        for (server in configuredServers) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.key_glass_selector_dark)
                setPadding(24, 24, 24, 24)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 16
                }
                layoutParams = params
            }

            val titleTv = TextView(this).apply {
                text = server.name
                setTextColor(getColor(R.color.glass_key_text_dark))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            card.addView(titleTv)

            val urlTv = TextView(this).apply {
                text = "URL: ${server.endpointUrl} (${server.transport})"
                setTextColor(getColor(R.color.glass_key_subtext_dark))
                textSize = 12f
                setPadding(0, 4, 0, 8)
            }
            card.addView(urlTv)

            val toolsTv = TextView(this).apply {
                text = "Tools: ${if (server.availableTools.isNotEmpty()) server.availableTools.joinToString(", ") else "None listed"}"
                setTextColor(getColor(R.color.secondary))
                textSize = 12f
                setPadding(0, 0, 0, 12)
            }
            card.addView(toolsTv)

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val testBtn = Button(this).apply {
                text = "Test Connection"
                textSize = 11f
                setBackgroundColor(getColor(R.color.primary))
                setTextColor(getColor(android.R.color.white))
                setOnClickListener {
                    testMcpServer(server)
                }
            }
            actionsRow.addView(testBtn)

            val deleteBtn = Button(this).apply {
                text = "Remove"
                textSize = 11f
                setBackgroundColor(getColor(R.color.status_red))
                setTextColor(getColor(android.R.color.white))
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 16
                }
                layoutParams = p
                setOnClickListener {
                    configuredServers.remove(server)
                    prefs.saveMcpServers(configuredServers)
                    renderServersList()
                }
            }
            actionsRow.addView(deleteBtn)

            card.addView(actionsRow)
            containerMcpServers.addView(card)
        }
    }

    private fun testMcpServer(server: McpServerConfig) {
        Toast.makeText(this, "Testing MCP Server: ${server.name}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val client = McpClient(server)
            val result = client.testConnection()
            if (result.isSuccess) {
                Toast.makeText(this@McpSettingsActivity, "✓ ${server.name}: Connected successfully!", Toast.LENGTH_SHORT).show()
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Connection failed"
                Toast.makeText(this@McpSettingsActivity, "✗ ${server.name}: $err", Toast.LENGTH_LONG).show()
            }
        }
    }
}
