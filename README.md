# Smart Keyboard AI Menu

Production-oriented, native Android Input Method (`InputMethodService`) engineered for Android 16 and Android 15 (API 26–36). Combines a polished glassmorphism design with offline smart typing, Arabic and English layouts (both utilizing Latin digits 0–9), and a user-triggered, privacy-first AI menu powered by Google Gemini, OpenAI-compatible APIs, and Model Context Protocol (MCP) tool servers.

---

## 1. Key Highlights & Features

### Real Android System Keyboard (Not a Demo)
- Registered via AndroidManifest `<service>` with `android.permission.BIND_INPUT_METHOD` and `@xml/method` metadata.
- Appears directly in Android Settings under **System > Languages & Input > On-screen Keyboard**.
- Includes complete interactive onboarding in `MainActivity` with one-tap shortcuts to system settings and keyboard switcher dialog (`InputMethodManager.showInputMethodPicker()`).
- Integrates seamlessly with Android's `InputConnection` to perform cursor positioning, character insertion, selective replacement, and editor actions (Done, Search, Send, Go, Next, Enter).

### Native Glass-Inspired Design & Tactile Feedback
- **Aesthetic:** Translucent frosted glass keys with subtle glowing borders, rounded corners, and responsive touch feedback.
- **Themes:** Dark Glass (default), Light Glass, and OLED Pure Black Glass.
- **Haptic & Sound Feedback:** System haptic feedback engine (`VibrationEffect` with fallback) and optional key click sounds.
- **Accessibility:** Touch target boundaries conforming to WCAG/Android guidelines (≥48dp) and support for reduced-motion accessibility settings.

### Bilingual Layouts with Latin Digits 0–9
- **Arabic Standard Layout:** Standard Arabic layout with letters (`ض ص ث ق ف غ ع ه خ ح ج د ش س ي ب ل ا ت ن م ك ط ئ ء ؤ ر ى ة و ز ظ`).
- **Latin Digits Standard:** As strictly required, the Arabic layout features **Latin digits 0–9** (`0, 1, 2, 3, 4, 5, 6, 7, 8, 9`), completely omitting Eastern Arabic digit glyphs.
- **Tashkeel / Shift Mode:** Arabic shift provides full Harakat (`َ`, `ً`, `ُ`, `ٌ`, `ِ`, `ٍ`, `ْ`, `ّ`) and punctuation (`؟`, `،`, `؛`).
- **English QWERTY Layout:** Standard QWERTY with dedicated numbers row, Shift/Caps Lock, and quick punctuation.
- **Symbols Layout:** Full punctuation set including `@, #, $, %, &, (, ), [, ], {, }, -, +, =, _, /, \, :, ;, ", ', !, ?`.

### Smart Local Typing
- **Offline First:** Word predictions, n-grams, and autocorrect run 100% locally on device using `DictionaryManager`. Keystrokes are **never** transmitted to remote servers.
- **Suggestion Strip:** Top toolbar presents up to 3 selectable predictions or corrections.
- **Sensitive Field Protection:** In password or sensitive fields (`TYPE_TEXT_VARIATION_PASSWORD`, `WEB_PASSWORD`, `FLAG_NO_SUGGESTIONS`), suggestions, learning, and AI features are strictly disabled.

### Collapsible AI Menu Drawer
- **Uncluttered Workspace:** Keeps the regular keyboard clean and accessible. AI tools live inside a collapsible drawer activated via the `✨ AI` button.
- **Non-Invasive AI Actions:**
  - **Correct Grammar:** Cleans up spelling, punctuation, and syntax while preserving meaning.
  - **Rewrite & Polish:** Enhances clarity, tone, and conciseness.
  - **Translate:** Translates text between Arabic, English, and other target languages.
  - **Summarize & Explain:** Breaks down complex text into key points or simple explanations.
  - **Fix Code & Unit Tests:** Analyzes snippets, resolves syntax/logic errors, and creates tests.
  - **Ask Assistant:** Direct prompt input field.
- **Explicit Review & Diff Inspection:** AI results are displayed in a diff review area. The keyboard **never** automatically replaces user text; explicit confirmation ("Insert at Cursor" or "Replace Selection") is required.

### Connection Status Lights (Green / Amber / Red)
The `✨ AI` toolbar button features a real-time status light:
- **Green (Connected):** The selected AI provider passed a real network handshake test.
- **Amber (Checking):** The connection is actively being verified.
- **Red (Disconnected / Misconfigured):** Missing API key, incorrect endpoint, network error, or HTTP error.
- **Gray (Disabled):** AI features disabled in settings.
- *Privacy Guarantee:* Tapping the status light displays a safe diagnosis message without leaking API keys, secrets, or tokens.

### Multi-Provider Architecture & Keystore Encryption
- **Google Gemini:** Direct integration with Google Gemini v1beta REST API (`gemini-1.5-flash`, `gemini-1.5-pro`).
- **OpenAI-Compatible:** Supports OpenAI, DeepSeek, Groq, Ollama, and local self-hosted relays.
- **Android Keystore Security:** API keys and credentials are encrypted on-device via `EncryptedSharedPreferences` backed by AES-256-GCM Keystore keys.
- **Model Context Protocol (MCP):** Connects to user-supplied MCP servers (SSE / JSON-RPC). MCP is treated strictly as a context and tool integration layer. Tools cannot run automatically; every tool invocation requires explicit user confirmation.

### Voice Typing & Termux Code Workflow
- **Voice Typing:** Android `SpeechRecognizer` activated **only** upon deliberate tap on the microphone key. Runtime `RECORD_AUDIO` permission is requested with clear explanation.
- **Termux & Coding Toolbar:** Quick syntax keys (`{ } ( ) [ ] -> == ;`) and safe Termux command preparation. Commands are copied to clipboard for manual execution—the keyboard never controls Termux automatically.

---

## 2. Project Architecture

```
com.smartkeyboard.aimenu/
├── SmartKeyboardService.kt         # Native Android InputMethodService
├── MainActivity.kt                 # Onboarding & System activation launcher
├── SettingsActivity.kt             # General keyboard preferences & theme picker
├── AiSettingsActivity.kt           # AI Provider & Encrypted API Key configuration
├── McpSettingsActivity.kt          # MCP Server & Tool configuration
├── PrivacyActivity.kt              # Transparent privacy notice & data wiping
├── data/
│   ├── Models.kt                   # Core enums and data classes
│   ├── KeyboardPreferences.kt      # EncryptedSharedPreferences wrapper
│   └── DictionaryManager.kt        # Local English/Arabic offline dictionary
├── ai/
│   ├── AiProvider.kt               # Provider interface
│   ├── GeminiProvider.kt           # Google Gemini API client
│   ├── OpenAiCompatibleProvider.kt # OpenAI/DeepSeek/Ollama client
│   ├── McpClient.kt                # Model Context Protocol client
│   └── AiManager.kt                # Central coordinator & security guard
├── keyboard/
│   ├── KeyboardLayoutManager.kt    # Arabic, English & Symbols layout tables
│   ├── KeyboardView.kt             # Custom glassmorphic view with touch handling
│   └── HapticFeedbackManager.kt    # Tactile vibration & sound feedback
└── voice/
    ├── SpeechRecognitionHelper.kt  # Android SpeechRecognizer wrapper
    └── TtsHelper.kt                # Text-to-Speech audio reader
```

---

## 3. Installation & Activation Guide (Samsung Android 16 / One UI 8)

### Step 1: Install the Application
Deploy the built APK to your device:
```bash
adb install -r SmartKeyboardAIMenu.apk
```

### Step 2: Enable the Keyboard
1. Open **Settings** on your phone.
2. Navigate to **General Management > Keyboard list and default**.
3. Locate **Smart Keyboard AI Menu** and toggle the switch to **ON**.
4. Confirm the Android system security prompt regarding third-party input methods.

*(Alternatively, launch the Smart Keyboard AI Menu app and tap **"Step 1: Open Keyboard Settings"**).*

### Step 3: Set as Active Keyboard
1. Tap on **Default keyboard** in the Samsung Keyboard list settings.
2. Select **Smart Keyboard AI Menu**.
3. Open any text field or the interactive test area in `MainActivity` to begin typing!

---

## 4. Privacy & Security Architecture

1. **Zero Keystroke Transmission:** Regular typing is evaluated strictly locally via on-device heuristics.
2. **Zero Password Leakage:** In sensitive or password fields, AI features, suggestions, and logging are completely locked out.
3. **No Automatic AI Submission:** Text is sent to the configured AI endpoint only when the user deliberately taps an AI action.
4. **No Background Telemetry:** The app contains zero analytics SDKs and zero ad tracking libraries.
5. **Client-Side Secrets Protection:** All API credentials reside in hardware-backed Android Keystore storage with full options to clear credentials on demand.
