package com.smartkeyboard.aimenu

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.smartkeyboard.aimenu.data.KeyboardPreferences
import com.smartkeyboard.aimenu.data.KeyboardThemeType

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = KeyboardPreferences(this)

        setupThemeSpinner()
        setupHeightSpinner()
        setupCheckboxes()
        setupNavigationButtons()
    }

    private fun setupThemeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerTheme)
        val themes = KeyboardThemeType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        spinner.adapter = adapter

        spinner.setSelection(prefs.theme.ordinal)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.theme = KeyboardThemeType.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupHeightSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerHeight)
        val heights = listOf("Compact (85%)", "Normal (100%)", "Large (115%)", "Extra Large (130%)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, heights)
        spinner.adapter = adapter

        val currentIdx = when (prefs.keyboardHeightPercent) {
            85 -> 0
            100 -> 1
            115 -> 2
            130 -> 3
            else -> 1
        }
        spinner.setSelection(currentIdx)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val pct = when (position) {
                    0 -> 85
                    1 -> 100
                    2 -> 115
                    3 -> 130
                    else -> 100
                }
                prefs.keyboardHeightPercent = pct
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCheckboxes() {
        val cbVibration = findViewById<CheckBox>(R.id.cbVibration)
        val cbSound = findViewById<CheckBox>(R.id.cbSound)
        val cbSuggestions = findViewById<CheckBox>(R.id.cbSuggestions)
        val cbAutocorrect = findViewById<CheckBox>(R.id.cbAutocorrect)
        val cbAutoCaps = findViewById<CheckBox>(R.id.cbAutoCaps)

        cbVibration.isChecked = prefs.vibrationEnabled
        cbVibration.setOnCheckedChangeListener { _, isChecked -> prefs.vibrationEnabled = isChecked }

        cbSound.isChecked = prefs.soundEnabled
        cbSound.setOnCheckedChangeListener { _, isChecked -> prefs.soundEnabled = isChecked }

        cbSuggestions.isChecked = prefs.suggestionsEnabled
        cbSuggestions.setOnCheckedChangeListener { _, isChecked -> prefs.suggestionsEnabled = isChecked }

        cbAutocorrect.isChecked = prefs.autocorrectEnabled
        cbAutocorrect.setOnCheckedChangeListener { _, isChecked -> prefs.autocorrectEnabled = isChecked }

        cbAutoCaps.isChecked = prefs.autoCapitalizeEnabled
        cbAutoCaps.setOnCheckedChangeListener { _, isChecked -> prefs.autoCapitalizeEnabled = isChecked }
    }

    private fun setupNavigationButtons() {
        findViewById<Button>(R.id.btnAiSettings).setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnMcpSettings).setOnClickListener {
            startActivity(Intent(this, McpSettingsActivity::class.java))
        }
    }
}
