package com.smartkeyboard.aimenu

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartkeyboard.aimenu.data.DictionaryManager
import com.smartkeyboard.aimenu.data.KeyboardPreferences

class PrivacyActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var dictionaryManager: DictionaryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        prefs = KeyboardPreferences(this)
        dictionaryManager = DictionaryManager(this)

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            dictionaryManager.clearLearnedHistory()
            Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearAllSecrets).setOnClickListener {
            prefs.clearCredentials()
            Toast.makeText(this, getString(R.string.credentials_deleted), Toast.LENGTH_SHORT).show()
        }
    }
}
