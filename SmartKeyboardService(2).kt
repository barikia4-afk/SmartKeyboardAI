package com.rizo.smartkb

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Smart AI Terminal Keyboard — InputMethodService.
 * Carbon Black theme, 3D raised neomorphic keys, glowing gradient letters.
 * Zero-Chat toolbar: Translate/TTS ⚡AI-Bash 🎤Voice 😀Emoji 🅰Lang ⚙Settings
 */
class SmartKeyboardService : InputMethodService() {

    private var keyboardView: View? = null
    private var currentLayout = LAYOUT_EN
    private var shift = false
    private var translateMode = true   // dual-mode: translate vs TTS reader
    private lateinit var ai: AiEngine
    private var tts: TtsManager? = null
    private var recognizer: SpeechRecognizer? = null

    companion object {
        const val LAYOUT_EN = 0
        const val LAYOUT_AR = 1
        const val LAYOUT_SYM = 2
        const val LAYOUT_EMOJI = 3
    }

    // ── layouts ───────────────────────────────────────────────────────────
    private val rowsEN = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("⇧","z","x","c","v","b","n","m","⌫"),
        listOf("🌐","😀","?123","space",".","↵")
    )
    private val rowsAR = listOf(
        listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج","د"),
        listOf("ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط"),
        listOf("ئ","ء","ؤ","ر","لا","ى","ة","و","ز","ظ","⌫"),
        listOf("🌐","😀","?123","space",".","↵")
    )
    private val rowsSYM = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","_","&","-","+","(",")","/"),
        listOf("~","`","|","•","√","π","÷","×","§","⌫"),
        listOf("🌐","😀","ABC","space",".","↵")
    )
    private val rowsEMOJI = listOf(
        listOf("😀","😂","🤣","😊","😍","😎","🤔","😴","😭","🔥"),
        listOf("👍","👎","🙏","💪","❤️","💯","✨","🎉","🚀","⚡"),
        listOf("🌐","😀","?123","space",".","↵")
    )

    override fun onCreate() {
        super.onCreate()
        ai = AiEngine(this)
    }

    override fun onCreateInputView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView = v
        tts = TtsManager(this)
        setupToolbar(v)
        renderKeys(v)
        return v
    }

    override fun onDestroy() {
        tts?.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }

    // ── toolbar (SwiftKey-style quick actions) ────────────────────────────
    private fun setupToolbar(v: View) {
        v.findViewById<Button>(R.id.btn_settings).setOnClickListener {
            val i = Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        v.findViewById<Button>(R.id.btn_lang).setOnClickListener {
            currentLayout = if (currentLayout == LAYOUT_EN) LAYOUT_AR else LAYOUT_EN
            renderKeys(v)
        }
        v.findViewById<Button>(R.id.btn_emoji).setOnClickListener {
            currentLayout = if (currentLayout == LAYOUT_EMOJI)
                (if (isArabicMode) LAYOUT_AR else LAYOUT_EN) else LAYOUT_EMOJI
            renderKeys(v)
        }
        // Dual-Mode button: tap = translate, long-press = TTS reader
        val btnTranslate = v.findViewById<Button>(R.id.btn_translate)
        btnTranslate.setOnClickListener { onTranslatePressed() }
        btnTranslate.setOnLongClickListener { onReadPressed(); true }
        v.findViewById<Button>(R.id.btn_ai).setOnClickListener { onAiPressed() }
        v.findViewById<Button>(R.id.btn_voice).setOnClickListener { onVoicePressed() }
    }

    private val isArabicMode get() = currentLayout == LAYOUT_AR

    /** Mode A1: translate captured screen text instantly */
    private fun onTranslatePressed() {
        val text = ScreenTextService.lastScreenText
        if (text.isBlank()) { toast("No captured screen text"); return }
        val target = prefs().getString("target_lang", "Arabic") ?: "Arabic"
        runPipeline { ai.translate(text, target) }
    }

    /** Mode A2: read highlighted text aloud via local TTS (no token cost) */
    private fun onReadPressed() {
        val text = ScreenTextService.lastScreenText
        if (text.isBlank()) { toast("No captured screen text"); return }
        tts?.speak(text)
    }

    /** Mode B: Smart Bash & Code Generation Engine (Zero-Chat) */
    private fun onAiPressed() {
        val captured = ScreenTextService.lastScreenText
        val editorText = currentInputConnection?.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val context = listOf(editorText, captured).filter { it.isNotBlank() }.joinToString("\n\n")
        if (context.isBlank()) { toast("Type or capture a terminal error first"); return }
        runPipeline {
            // if it looks like an error/log → bash fix, else → continue building
            if (looksLikeError(context)) ai.bashFromError(context)
            else ai.continueCode(context)
        }
    }

    private fun looksLikeError(s: String): Boolean {
        val markers = listOf("error", "Error", "ERROR", "failed", "Failed", "exception",
            "Exception", "Traceback", "panic", "denied", "not found", "E:")
        return markers.any { s.contains(it) }
    }

    /** run Zero-Chat pipeline: API → clipboard → type result into field */
    private fun runPipeline(block: suspend () -> AiEngine.Result) {
        toast("⚡ AI working…")
        CoroutineScope(Dispatchers.Main).launch {
            when (val r = block()) {
                is AiEngine.Ok -> {
                    val cmd = r.text
                    copyToClipboard(cmd)
                    currentInputConnection?.commitText(cmd, 1)
                    toast("✔ Bash copied + pasted")
                }
                is AiEngine.Fail -> toast("✖ ${r.error}")
            }
        }
    }

    private fun onVoicePressed() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice recognition unavailable"); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val m = results.getStringArrayList(RecognizerIntent.EXTRA_RESULTS_RESULTS)
                        ?: results.getStringArrayList("results_recognition")
                    m?.firstOrNull()?.let { currentInputConnection?.commitText(it + " ", 1) }
                }
                override fun onError(error: Int) { toast("Voice error $error") }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
        toast("🎤 Listening…")
    }

    // ── key rendering: 3D raised neomorphic keys ──────────────────────────
    private fun renderKeys(v: View) {
        val container = v.findViewById<LinearLayout>(R.id.keys_container)
        container.removeAllViews()
        val rows = when (currentLayout) {
            LAYOUT_EN -> rowsEN
            LAYOUT_AR -> rowsAR
            LAYOUT_SYM -> rowsSYM
            else -> rowsEMOJI
        }
        for (row in rows) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 3, 0, 3) }
            }
            for (key in row) rowView.addView(makeKey(key))
            container.addView(rowView)
        }
    }

    private fun makeKey(label: String): Button {
        val weight = when (label) {
            "space" -> 4.0f
            "?123", "ABC" -> 1.6f
            "⌫" -> 1.6f
            "⇧" -> 1.6f
            "↵" -> 1.8f
            else -> 1.0f
        }
        val bg = when (label) {
            "⚡" -> R.drawable.key_bg_action
            "⌫", "⇧", "↵", "space", "🌐", "?123", "ABC" -> R.drawable.key_bg_special
            else -> R.drawable.key_bg
        }
        return Button(this).apply {
            text = displayLabel(label)
            textSize = if (label.length > 1) 13f else 19f
            setTextColor(getColor(R.color.key_text))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundResource(bg)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                setMargins(3, 0, 3, 0)
            }
            minHeight = 0
            minimumHeight = 0
            minimumWidth = 0
            minWidth = 0
            setPadding(0, 22, 0, 22)
            setOnClickListener { onKey(label) }
        }
    }

    private fun displayLabel(label: String): String = when (label) {
        "space" -> "space"
        "⌫" -> "⌫"
        "⇧" -> if (shift) "⇪" else "⇧"
        "↵" -> "↵"
        "?123" -> "?123"
        "ABC" -> "ABC"
        else -> if (shift && label.length == 1 && currentLayout == LAYOUT_EN)
            label.uppercase() else label
    }

    private fun onKey(label: String) {
        val ic = currentInputConnection ?: return
        when (label) {
            "⌫" -> ic.deleteSurroundingText(1, 0)
            "↵" -> ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED)
            "space" -> ic.commitText(" ", 1)
            "⇧" -> { shift = !shift; keyboardView?.let { renderKeys(it) } }
            "🌐" -> {
                currentLayout = if (isArabicMode) LAYOUT_EN else LAYOUT_AR
                keyboardView?.let { renderKeys(it) }
            }
            "😀" -> {
                currentLayout = if (currentLayout == LAYOUT_EMOJI)
                    (if (isArabicMode) LAYOUT_AR else LAYOUT_EN) else LAYOUT_EMOJI
                keyboardView?.let { renderKeys(it) }
            }
            "?123" -> { currentLayout = LAYOUT_SYM; keyboardView?.let { renderKeys(it) } }
            "ABC" -> { currentLayout = LAYOUT_EN; keyboardView?.let { renderKeys(it) } }
            else -> {
                ic.commitText(displayLabel(label), 1)
                if (shift) { shift = false; keyboardView?.let { renderKeys(it) } }
            }
        }
        updateSuggestions()
    }

    // ── simple local word suggestions (SwiftKey-style strip) ──────────────
    private val commonEN = listOf("the", "and", "for", "you", "this", "with", "that", "have")
    private val commonAR = listOf("في", "من", "على", "ان", "انا", "هذا", "الي", "كان")

    private fun updateSuggestions() {
        val v = keyboardView ?: return
        val typed = currentInputConnection?.getTextBeforeCursor(12, 0)?.toString() ?: ""
        val pool = if (isArabicMode) commonAR else commonEN
        val sug = pool.filter { it.startsWith(typed.trim(), ignoreCase = true) }.take(3)
        val views = listOf(R.id.sug1, R.id.sug2, R.id.sug3)
        views.forEachIndexed { i, id ->
            val tv = v.findViewById<TextView>(id)
            tv.text = sug.getOrElse(i) { "" }
            tv.setOnClickListener {
                val word = tv.text.toString()
                if (word.isNotEmpty()) {
                    currentInputConnection?.deleteSurroundingText(typed.length, 0)
                    currentInputConnection?.commitText("$word ", 1)
                }
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────
    private fun prefs() = getSharedPreferences("smartkb_prefs", MODE_PRIVATE)

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("bash", text))
    }

    private fun toast(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@SmartKeyboardService, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
