package com.smartkeyboard.aimenu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.smartkeyboard.aimenu.ai.AiManager
import com.smartkeyboard.aimenu.data.AiAction
import com.smartkeyboard.aimenu.data.ConnectionStatus
import com.smartkeyboard.aimenu.data.DictionaryManager
import com.smartkeyboard.aimenu.data.KeyActionType
import com.smartkeyboard.aimenu.data.KeyboardMode
import com.smartkeyboard.aimenu.data.KeyboardPreferences
import com.smartkeyboard.aimenu.data.ShiftState
import com.smartkeyboard.aimenu.keyboard.KeyboardView
import com.smartkeyboard.aimenu.voice.SpeechRecognitionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Locale

class SmartKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: KeyboardPreferences
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var aiManager: AiManager
    private lateinit var speechHelper: SpeechRecognitionHelper

    private var rootView: View? = null
    private var customKeyboardView: KeyboardView? = null
    private var suggestionStrip: LinearLayout? = null
    private var suggestionViews = mutableListOf<TextView>()
    private var statusIndicator: View? = null
    private var aiCollapsiblePanel: LinearLayout? = null
    private var codeToolbar: View? = null
    private var etAiInputPrompt: EditText? = null
    private var tvAiResponseReview: TextView? = null
    private var pbAiLoading: ProgressBar? = null

    private var currentMode = KeyboardMode.ENGLISH
    private var currentShiftState = ShiftState.OFF
    private var currentComposingWord = StringBuilder()
    private var isCurrentFieldSensitive = false
    private var lastAiResult: String = ""

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        dictionaryManager = DictionaryManager(this)
        aiManager = AiManager(this, serviceScope)

        speechHelper = SpeechRecognitionHelper(
            context = this,
            onTextRecognized = { recognizedText ->
                currentInputConnection?.commitText("$recognizedText ", 1)
            },
            onListeningStateChanged = { isListening ->
                if (isListening) {
                    Toast.makeText(this, getString(R.string.speech_listening), Toast.LENGTH_SHORT).show()
                }
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_main_layout, null)
        rootView = view

        customKeyboardView = view.findViewById(R.id.customKeyboardView)
        suggestionStrip = view.findViewById(R.id.suggestionStrip)
        statusIndicator = view.findViewById(R.id.statusIndicator)
        aiCollapsiblePanel = view.findViewById(R.id.aiCollapsiblePanel)
        codeToolbar = view.findViewById(R.id.codeToolbar)
        etAiInputPrompt = view.findViewById(R.id.etAiInputPrompt)
        tvAiResponseReview = view.findViewById(R.id.tvAiResponseReview)
        pbAiLoading = view.findViewById(R.id.pbAiLoading)

        setupToolbar(view)
        setupSuggestions(view)
        setupAiPanel(view)
        setupCodeToolbar(view)
        setupKeyboardView()

        // Sync connection status listener
        aiManager.onStatusChanged = { status ->
            updateStatusIndicatorLight(status)
        }
        updateStatusIndicatorLight(aiManager.currentStatus)

        return view
    }

    private fun setupToolbar(view: View) {
        val btnToggleAi = view.findViewById<View>(R.id.btnToggleAiPanel)
        btnToggleAi.setOnClickListener {
            toggleAiPanel()
        }

        // Tap status light for safe, non-secret diagnostic explanation
        statusIndicator?.setOnClickListener {
            val status = aiManager.currentStatus
            Toast.makeText(this, "AI Status: ${status.description}", Toast.LENGTH_SHORT).show()
            if (status != ConnectionStatus.CONNECTED && status != ConnectionStatus.CHECKING) {
                aiManager.verifyConnection()
            }
        }

        val btnToggleCode = view.findViewById<View>(R.id.btnToggleCodeToolbar)
        btnToggleCode.setOnClickListener {
            codeToolbar?.let {
                it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        val btnVoice = view.findViewById<View>(R.id.btnVoiceTyping)
        btnVoice.setOnClickListener {
            if (speechHelper.isListening) {
                speechHelper.stopListening()
            } else {
                val locale = if (currentMode == KeyboardMode.ARABIC) Locale("ar") else Locale.US
                speechHelper.startListening(locale)
            }
        }

        val btnSettings = view.findViewById<View>(R.id.btnOpenSettingsShortcut)
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun setupSuggestions(view: View) {
        val s1 = view.findViewById<TextView>(R.id.suggestion1)
        val s2 = view.findViewById<TextView>(R.id.suggestion2)
        val s3 = view.findViewById<TextView>(R.id.suggestion3)
        suggestionViews.clear()
        suggestionViews.addAll(listOf(s1, s2, s3))

        suggestionViews.forEach { tv ->
            tv.setOnClickListener {
                val chosen = tv.text.toString()
                if (chosen.isNotBlank()) {
                    applySuggestion(chosen)
                }
            }
        }
    }

    private fun setupAiPanel(view: View) {
        view.findViewById<View>(R.id.btnCloseAiPanel).setOnClickListener {
            aiCollapsiblePanel?.visibility = View.GONE
        }

        view.findViewById<Button>(R.id.btnSendAiPrompt).setOnClickListener {
            val prompt = etAiInputPrompt?.text?.toString() ?: ""
            if (prompt.isNotBlank()) {
                triggerAiAction(AiAction.ASK, prompt)
            }
        }

        // Action chips
        view.findViewById<View>(R.id.chipCorrect).setOnClickListener {
            triggerAiAction(AiAction.CORRECT, getEditorSelectedOrSurroundingText())
        }
        view.findViewById<View>(R.id.chipRewrite).setOnClickListener {
            triggerAiAction(AiAction.REWRITE, getEditorSelectedOrSurroundingText())
        }
        view.findViewById<View>(R.id.chipTranslate).setOnClickListener {
            val target = if (currentMode == KeyboardMode.ARABIC) "English" else "Arabic"
            triggerAiAction(AiAction.TRANSLATE, getEditorSelectedOrSurroundingText(), targetLanguage = target)
        }
        view.findViewById<View>(R.id.chipSummarize).setOnClickListener {
            triggerAiAction(AiAction.SUMMARIZE, getEditorSelectedOrSurroundingText())
        }
        view.findViewById<View>(R.id.chipExplain).setOnClickListener {
            triggerAiAction(AiAction.EXPLAIN, getEditorSelectedOrSurroundingText())
        }
        view.findViewById<View>(R.id.chipFixCode).setOnClickListener {
            triggerAiAction(AiAction.FIX_CODE, getEditorSelectedOrSurroundingText())
        }
        view.findViewById<View>(R.id.chipUnitTests).setOnClickListener {
            triggerAiAction(AiAction.GENERATE_TESTS, getEditorSelectedOrSurroundingText())
        }

        // Explicit confirmation buttons (Never auto-replaces!)
        view.findViewById<Button>(R.id.btnApplyToEditor).setOnClickListener {
            if (lastAiResult.isNotBlank()) {
                currentInputConnection?.commitText(lastAiResult, 1)
                aiCollapsiblePanel?.visibility = View.GONE
            }
        }

        view.findViewById<Button>(R.id.btnReplaceSelection).setOnClickListener {
            if (lastAiResult.isNotBlank()) {
                currentInputConnection?.commitText(lastAiResult, 1)
                aiCollapsiblePanel?.visibility = View.GONE
            }
        }

        view.findViewById<Button>(R.id.btnCopyResponse).setOnClickListener {
            if (lastAiResult.isNotBlank()) {
                copyToClipboard("AI Response", lastAiResult)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCodeToolbar(view: View) {
        val insertSymbol: (String) -> Unit = { sym ->
            currentInputConnection?.commitText(sym, 1)
        }

        view.findViewById<View>(R.id.codeSymCurlOpen).setOnClickListener { insertSymbol("{") }
        view.findViewById<View>(R.id.codeSymCurlClose).setOnClickListener { insertSymbol("}") }
        view.findViewById<View>(R.id.codeSymParenOpen).setOnClickListener { insertSymbol("(") }
        view.findViewById<View>(R.id.codeSymParenClose).setOnClickListener { insertSymbol(")") }
        view.findViewById<View>(R.id.codeSymBracketOpen).setOnClickListener { insertSymbol("[") }
        view.findViewById<View>(R.id.codeSymBracketClose).setOnClickListener { insertSymbol("]") }
        view.findViewById<View>(R.id.codeSymArrow).setOnClickListener { insertSymbol("->") }
        view.findViewById<View>(R.id.codeSymEquals).setOnClickListener { insertSymbol("==") }
        view.findViewById<View>(R.id.codeSymSemi).setOnClickListener { insertSymbol(";") }

        // Termux preparation
        view.findViewById<View>(R.id.btnTermuxPrepare).setOnClickListener {
            val cmd = getEditorSelectedOrSurroundingText().trim()
            if (cmd.isNotBlank()) {
                copyToClipboard("Termux Command", cmd)
                Toast.makeText(this, getString(R.string.termux_copied), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Select or type a command to prepare for Termux", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupKeyboardView() {
        customKeyboardView?.apply {
            keyboardMode = currentMode
            shiftState = currentShiftState
            onKeyPressListener = { key ->
                handleKeyPress(key)
            }
        }
    }

    private fun handleKeyPress(key: com.smartkeyboard.aimenu.data.KeyModel) {
        val ic = currentInputConnection ?: return

        when (key.actionType) {
            KeyActionType.CHARACTER -> {
                val charToCommit = key.primaryLabel
                ic.commitText(charToCommit, 1)
                currentComposingWord.append(charToCommit)
                updateSuggestions()

                // If shifted (not caps lock), return to unshifted
                if (currentShiftState == ShiftState.SHIFTED) {
                    currentShiftState = ShiftState.OFF
                    customKeyboardView?.shiftState = currentShiftState
                }
            }

            KeyActionType.BACKSPACE -> {
                if (currentComposingWord.isNotEmpty()) {
                    currentComposingWord.deleteCharAt(currentComposingWord.length - 1)
                }
                val selected = ic.getSelectedText(0)
                if (selected != null && selected.isNotEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                updateSuggestions()
            }

            KeyActionType.SPACE -> {
                if (currentComposingWord.isNotEmpty()) {
                    dictionaryManager.learnWord(currentComposingWord.toString(), isCurrentFieldSensitive)
                    currentComposingWord.clear()
                }
                ic.commitText(" ", 1)
                clearSuggestions()
            }

            KeyActionType.SHIFT -> {
                currentShiftState = when (currentShiftState) {
                    ShiftState.OFF -> ShiftState.SHIFTED
                    ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                customKeyboardView?.shiftState = currentShiftState
            }

            KeyActionType.ENTER -> {
                if (currentComposingWord.isNotEmpty()) {
                    dictionaryManager.learnWord(currentComposingWord.toString(), isCurrentFieldSensitive)
                    currentComposingWord.clear()
                }
                clearSuggestions()

                val info = currentInputEditorInfo
                val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

                if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.commitText("\n", 1)
                }
            }

            KeyActionType.SWITCH_MODE -> {
                currentMode = if (currentMode == KeyboardMode.SYMBOLS) {
                    if (prefs.defaultLanguage == "ar") KeyboardMode.ARABIC else KeyboardMode.ENGLISH
                } else {
                    KeyboardMode.SYMBOLS
                }
                customKeyboardView?.keyboardMode = currentMode
            }

            KeyActionType.SWITCH_LANGUAGE -> {
                currentMode = if (currentMode == KeyboardMode.ARABIC) {
                    KeyboardMode.ENGLISH
                } else {
                    KeyboardMode.ARABIC
                }
                customKeyboardView?.keyboardMode = currentMode
            }

            else -> {}
        }
    }

    private fun updateSuggestions() {
        if (isCurrentFieldSensitive || !prefs.suggestionsEnabled) {
            clearSuggestions()
            return
        }

        val word = currentComposingWord.toString()
        if (word.isEmpty()) {
            clearSuggestions()
            return
        }

        val isArabic = (currentMode == KeyboardMode.ARABIC)
        val candidates = dictionaryManager.getSuggestions(word, isArabic)

        for (i in suggestionViews.indices) {
            if (i < candidates.size) {
                suggestionViews[i].text = candidates[i]
                suggestionViews[i].visibility = View.VISIBLE
            } else {
                suggestionViews[i].text = ""
                suggestionViews[i].visibility = View.INVISIBLE
            }
        }
    }

    private fun clearSuggestions() {
        suggestionViews.forEach {
            it.text = ""
            it.visibility = View.INVISIBLE
        }
    }

    private fun applySuggestion(chosen: String) {
        val ic = currentInputConnection ?: return
        val currentLen = currentComposingWord.length
        if (currentLen > 0) {
            ic.deleteSurroundingText(currentLen, 0)
        }
        ic.commitText("$chosen ", 1)
        dictionaryManager.learnWord(chosen, isCurrentFieldSensitive)
        currentComposingWord.clear()
        clearSuggestions()
    }

    private fun toggleAiPanel() {
        aiCollapsiblePanel?.let { panel ->
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun triggerAiAction(
        action: AiAction,
        input: String,
        targetLanguage: String = "English",
        codeLanguage: String = "Kotlin"
    ) {
        if (isCurrentFieldSensitive) {
            Toast.makeText(this, "AI features are disabled in password / sensitive fields.", Toast.LENGTH_SHORT).show()
            return
        }

        pbAiLoading?.visibility = View.VISIBLE
        tvAiResponseReview?.text = "Sending request to AI provider…"

        aiManager.executeAction(
            action = action,
            inputText = input,
            targetLanguage = targetLanguage,
            codeLanguage = codeLanguage,
            isSensitiveField = isCurrentFieldSensitive
        ) { result ->
            pbAiLoading?.visibility = View.GONE
            if (result.isSuccess) {
                lastAiResult = result.getOrNull() ?: ""
                tvAiResponseReview?.text = buildDiffOrPreviewText(input, lastAiResult)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                tvAiResponseReview?.text = "Error: $err"
            }
        }
    }

    private fun buildDiffOrPreviewText(original: String, proposed: String): String {
        return if (original.isNotBlank() && original != proposed) {
            "--- Original ---\n$original\n\n+++ Proposed AI Changes +++\n$proposed"
        } else {
            proposed
        }
    }

    private fun getEditorSelectedOrSurroundingText(): String {
        val ic = currentInputConnection ?: return ""
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) {
            return selected
        }
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(100, 0)?.toString() ?: ""
        return (before + after).trim()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun updateStatusIndicatorLight(status: ConnectionStatus) {
        statusIndicator?.let { dot ->
            val color = ContextCompat.getColor(this, status.colorRes)
            dot.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentComposingWord.clear()
        clearSuggestions()

        // 1. Detect password and sensitive fields
        if (info != null) {
            val inputType = info.inputType
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isPassword = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                 variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
                (inputType and InputType.TYPE_CLASS_NUMBER != 0 && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

            isCurrentFieldSensitive = isPassword || ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)

            // Update Enter label based on Editor Action
            val actionId = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val label = when (actionId) {
                EditorInfo.IME_ACTION_DONE -> getString(R.string.key_action_done)
                EditorInfo.IME_ACTION_SEARCH -> getString(R.string.key_action_search)
                EditorInfo.IME_ACTION_SEND -> getString(R.string.key_action_send)
                EditorInfo.IME_ACTION_GO -> getString(R.string.key_action_go)
                EditorInfo.IME_ACTION_NEXT -> getString(R.string.key_action_next)
                else -> "↵"
            }
            customKeyboardView?.enterActionLabel = label
        }

        // Set default layout from preferences if starting fresh
        if (!restarting) {
            currentMode = if (prefs.defaultLanguage == "ar") KeyboardMode.ARABIC else KeyboardMode.ENGLISH
            customKeyboardView?.keyboardMode = currentMode
        }

        // Theme and height sync
        customKeyboardView?.updateThemeColors()
        aiManager.verifyConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper.stopListening()
        serviceScope.cancel()
    }
}
