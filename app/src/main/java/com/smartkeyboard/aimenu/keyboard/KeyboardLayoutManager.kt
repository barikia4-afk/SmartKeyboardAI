package com.smartkeyboard.aimenu.keyboard

import com.smartkeyboard.aimenu.data.KeyActionType
import com.smartkeyboard.aimenu.data.KeyModel
import com.smartkeyboard.aimenu.data.KeyboardMode
import com.smartkeyboard.aimenu.data.ShiftState

object KeyboardLayoutManager {

    /**
     * Builds key rows based on active mode (English, Arabic, Symbols) and shift state.
     * Guaranteed: Arabic layout uses Latin digits (0-9) - NEVER Eastern Arabic digits!
     */
    fun getLayoutRows(mode: KeyboardMode, shiftState: ShiftState): List<List<KeyModel>> {
        return when (mode) {
            KeyboardMode.ENGLISH -> getEnglishLayout(shiftState)
            KeyboardMode.ARABIC -> getArabicLayout(shiftState)
            KeyboardMode.SYMBOLS -> getSymbolsLayout()
        }
    }

    private fun getEnglishLayout(shiftState: ShiftState): List<List<KeyModel>> {
        val isShifted = shiftState != ShiftState.OFF
        val rows = mutableListOf<List<KeyModel>>()

        // Row 0: Latin digits 0-9
        rows.add(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
                KeyModel(primaryLabel = it)
            }
        )

        // Row 1: QWERTY
        val r1 = if (isShifted) "QWERTYUIOP" else "qwertyuiop"
        rows.add(r1.map { KeyModel(primaryLabel = it.toString()) })

        // Row 2: ASDFGHJKL
        val r2 = if (isShifted) "ASDFGHJKL" else "asdfghjkl"
        rows.add(r2.map { KeyModel(primaryLabel = it.toString()) })

        // Row 3: Shift + ZXCVBNM + Backspace
        val r3 = mutableListOf<KeyModel>()
        r3.add(
            KeyModel(
                primaryLabel = if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧",
                isSpecial = true,
                widthWeight = 1.4f,
                actionType = KeyActionType.SHIFT
            )
        )
        val r3Letters = if (isShifted) "ZXCVBNM" else "zxcvbnm"
        r3Letters.forEach { r3.add(KeyModel(primaryLabel = it.toString())) }
        r3.add(
            KeyModel(
                primaryLabel = "⌫",
                isSpecial = true,
                widthWeight = 1.4f,
                actionType = KeyActionType.BACKSPACE
            )
        )
        rows.add(r3)

        // Row 4: Mode Switch (?123) + Language (Globe) + Space + Period + Enter
        val r4 = listOf(
            KeyModel("?123", isSpecial = true, widthWeight = 1.3f, actionType = KeyActionType.SWITCH_MODE),
            KeyModel("🌐", isSpecial = true, widthWeight = 1.0f, actionType = KeyActionType.SWITCH_LANGUAGE),
            KeyModel("English", widthWeight = 4.2f, actionType = KeyActionType.SPACE),
            KeyModel(".", widthWeight = 1.0f),
            KeyModel("↵", isSpecial = true, widthWeight = 1.5f, actionType = KeyActionType.ENTER)
        )
        rows.add(r4)

        return rows
    }

    private fun getArabicLayout(shiftState: ShiftState): List<List<KeyModel>> {
        val isShifted = shiftState != ShiftState.OFF
        val rows = mutableListOf<List<KeyModel>>()

        // Row 0: Latin digits 0-9 (Strictly Latin digits 0-9, NOT Eastern Arabic!)
        rows.add(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
                KeyModel(primaryLabel = it)
            }
        )

        // Standard Arabic layout
        if (!isShifted) {
            // Normal Arabic characters
            // Row 1: ض ص ث ق ف غ ع ه خ ح ج د
            rows.add(listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د").map { KeyModel(it) })

            // Row 2: ش س ي ب ل ا ت ن م ك ط
            rows.add(listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط").map { KeyModel(it) })

            // Row 3: Shift (Harakat) + ئ ء ؤ ر ى ة و ز ظ + Backspace
            val r3 = mutableListOf<KeyModel>()
            r3.add(KeyModel("تشكيل", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.SHIFT))
            listOf("ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ظ").forEach { r3.add(KeyModel(it)) }
            r3.add(KeyModel("⌫", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.BACKSPACE))
            rows.add(r3)
        } else {
            // Tashkeel / Harakat & Extended Arabic Glyphs
            // Row 1: َ (فتحة), ً (تنوين فتح), ُ (ضمة), ٌ (تنوين ضم), ِ (كسرة), ٍ (تنوين كسر), ْ (سكون), ّ (شدة), آ, إ, أ, ء
            rows.add(listOf("َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ْ", "ّ", "آ", "إ", "أ", "ء").map { KeyModel(it) })

            // Row 2: ـ (تطويل), «, », ؟, ؛, :, "ـ", "!", "؟", "؛", "،"
            rows.add(listOf("ـ", "«", "»", "؟", "؛", "،", "!", "٪", "×", "÷", "=").map { KeyModel(it) })

            // Row 3: Shift (back to letters) + Special marks
            val r3 = mutableListOf<KeyModel>()
            r3.add(KeyModel("أبجد", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.SHIFT))
            listOf("ـ", "“", "”", "‘", "’", "[", "]", "{", "}").forEach { r3.add(KeyModel(it)) }
            r3.add(KeyModel("⌫", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.BACKSPACE))
            rows.add(r3)
        }

        // Row 4: Mode Switch (?123) + Language Switch (Globe) + Space + Arabic Comma + Enter
        val r4 = listOf(
            KeyModel("?123", isSpecial = true, widthWeight = 1.3f, actionType = KeyActionType.SWITCH_MODE),
            KeyModel("🌐", isSpecial = true, widthWeight = 1.0f, actionType = KeyActionType.SWITCH_LANGUAGE),
            KeyModel("العربية", widthWeight = 4.2f, actionType = KeyActionType.SPACE),
            KeyModel("،", widthWeight = 1.0f),
            KeyModel("↵", isSpecial = true, widthWeight = 1.5f, actionType = KeyActionType.ENTER)
        )
        rows.add(r4)

        return rows
    }

    private fun getSymbolsLayout(): List<List<KeyModel>> {
        val rows = mutableListOf<List<KeyModel>>()

        // Row 0: Latin Digits 0-9
        rows.add(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyModel(it) }
        )

        // Row 1: Common symbols
        rows.add(
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map { KeyModel(it) }
        )

        // Row 2: Secondary symbols
        rows.add(
            listOf("=", "*", "\"", "'", ":", ";", "!", "?", "\\", "_").map { KeyModel(it) }
        )

        // Row 3: Brackets, braces, logic
        val r3 = mutableListOf<KeyModel>()
        listOf("~", "`", "|", "<", ">", "{", "}", "[", "]").forEach {
            r3.add(KeyModel(it))
        }
        r3.add(KeyModel("⌫", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.BACKSPACE))
        rows.add(r3)

        // Row 4: Return to letters (ABC / أبج) + Globe + Space + Period + Enter
        val r4 = listOf(
            KeyModel("ABC", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.SWITCH_MODE),
            KeyModel("🌐", isSpecial = true, widthWeight = 1.0f, actionType = KeyActionType.SWITCH_LANGUAGE),
            KeyModel("Space", widthWeight = 4.2f, actionType = KeyActionType.SPACE),
            KeyModel(".", widthWeight = 1.0f),
            KeyModel("↵", isSpecial = true, widthWeight = 1.4f, actionType = KeyActionType.ENTER)
        )
        rows.add(r4)

        return rows
    }
}
