package com.smartkeyboard.aimenu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvEnabledStatus: TextView
    private lateinit var tvSelectedStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEnabledStatus = findViewById(R.id.tvEnabledStatus)
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus)

        // Step 1: Open system keyboard settings
        findViewById<Button>(R.id.btnEnableIme).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        // Step 2: Open system keyboard picker
        findViewById<Button>(R.id.btnSelectIme).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // Navigation
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateImeStatus()
    }

    private fun updateImeStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledList = imm.enabledInputMethodList
        val myPackage = packageName

        var isEnabled = false
        for (info in enabledList) {
            if (info.packageName == myPackage) {
                isEnabled = true
                break
            }
        }

        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val isSelected = currentIme.contains(myPackage)

        if (isEnabled) {
            tvEnabledStatus.text = getString(R.string.status_enabled_yes)
            tvEnabledStatus.setTextColor(getColor(R.color.status_green))
        } else {
            tvEnabledStatus.text = getString(R.string.status_enabled_no)
            tvEnabledStatus.setTextColor(getColor(R.color.status_red))
        }

        if (isSelected) {
            tvSelectedStatus.text = getString(R.string.status_selected_yes)
            tvSelectedStatus.setTextColor(getColor(R.color.status_green))
        } else {
            tvSelectedStatus.text = getString(R.string.status_selected_no)
            tvSelectedStatus.setTextColor(getColor(R.color.status_red))
        }
    }
}
