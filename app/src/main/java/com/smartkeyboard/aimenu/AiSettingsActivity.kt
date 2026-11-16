package com.smartkeyboard.aimenu

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartkeyboard.aimenu.ai.AiManager
import com.smartkeyboard.aimenu.data.AiProviderType
import com.smartkeyboard.aimenu.data.ConnectionStatus
import com.smartkeyboard.aimenu.data.KeyboardPreferences

class AiSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var aiManager: AiManager

    private lateinit var cbAiEnabled: CheckBox
    private lateinit var spinnerProvider: Spinner
    private lateinit var etModelId: EditText
    private lateinit var etEndpoint: EditText
    private lateinit var etApiKey: EditText
    private lateinit var viewStatusLight: View
    private lateinit var tvStatusExplanation: TextView
    private lateinit var btnTestConnection: Button
    private lateinit var btnDeleteCredentials: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_settings)

        prefs = KeyboardPreferences(this)
        aiManager = AiManager(this, lifecycleScope)

        cbAiEnabled = findViewById(R.id.cbAiEnabled)
        spinnerProvider = findViewById(R.id.spinnerProvider)
        etModelId = findViewById(R.id.etModelId)
        etEndpoint = findViewById(R.id.etEndpoint)
        etApiKey = findViewById(R.id.etApiKey)
        viewStatusLight = findViewById(R.id.viewStatusLight)
        tvStatusExplanation = findViewById(R.id.tvStatusExplanation)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnDeleteCredentials = findViewById(R.id.btnDeleteCredentials)

        loadPreferences()
        setupListeners()

        aiManager.verifyConnection { status ->
            updateStatusUi(status)
        }
    }

    private fun loadPreferences() {
        cbAiEnabled.isChecked = prefs.aiEnabled
        etModelId.setText(prefs.aiModel)
        etEndpoint.setText(prefs.aiEndpoint)
        etApiKey.setText(prefs.apiKey)

        val providers = AiProviderType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        spinnerProvider.adapter = adapter
        spinnerProvider.setSelection(prefs.aiProvider.ordinal)
    }

    private fun setupListeners() {
        cbAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.aiEnabled = isChecked
            aiManager.verifyConnection { updateStatusUi(it) }
        }

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = AiProviderType.values()[position]
                if (prefs.aiProvider != selectedType) {
                    prefs.aiProvider = selectedType
                    // Populate default endpoints and models if empty
                    if (selectedType == AiProviderType.GEMINI) {
                        etModelId.setText("gemini-1.5-flash")
                        etEndpoint.setText("https://generativelanguage.googleapis.com")
                    } else {
                        etModelId.setText("gpt-4o-mini")
                        etEndpoint.setText("https://api.openai.com/v1")
                    }
                    saveInputs()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnTestConnection.setOnClickListener {
            saveInputs()
            updateStatusUi(ConnectionStatus.CHECKING)
            aiManager.verifyConnection { status ->
                updateStatusUi(status)
                Toast.makeText(this, "Result: ${status.description}", Toast.LENGTH_SHORT).show()
            }
        }

        btnDeleteCredentials.setOnClickListener {
            prefs.clearCredentials()
            etApiKey.setText("")
            Toast.makeText(this, getString(R.string.credentials_deleted), Toast.LENGTH_SHORT).show()
            aiManager.verifyConnection { updateStatusUi(it) }
        }
    }

    private fun saveInputs() {
        prefs.aiModel = etModelId.text.toString().trim()
        prefs.aiEndpoint = etEndpoint.text.toString().trim()
        prefs.apiKey = etApiKey.text.toString().trim()
    }

    private fun updateStatusUi(status: ConnectionStatus) {
        val color = ContextCompat.getColor(this, status.colorRes)
        viewStatusLight.backgroundTintList = ColorStateList.valueOf(color)
        tvStatusExplanation.text = status.description
    }

    override fun onPause() {
        super.onPause()
        saveInputs()
    }
}
