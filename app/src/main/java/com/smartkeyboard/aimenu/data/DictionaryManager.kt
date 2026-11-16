package com.smartkeyboard.aimenu.data

import android.content.Context
import java.util.Locale

class DictionaryManager(context: Context) {

    // Common English words & frequencies
    private val englishWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "keyboard", "smart", "menu", "android", "project", "kotlin", "code", "ai", "termux"
    )

    // Standard Arabic vocabulary (Modern Standard Arabic)
    private val arabicWords = listOf(
        "في", "من", "على", "إلى", "أن", "هذا", "هذه", "التي", "الذي", "عن",
        "مع", "كل", "هو", "هي", "ما", "كان", "كانت", "لقد", "ذلك", "تلك",
        "بين", "بعد", "قبل", "عند", "حتى", "ثم", "أو", "لا", "نعم", "غير",
        "كما", "له", "لها", "لهم", "فيه", "فيها", "منه", "منها", "عليه", "عليها",
        "يوم", "سنة", "عام", "عمل", "وقت", "شيء", "أمر", "مكان", "طريق", "حياة",
        "الله", "السلام", "عليكم", "مرحبا", "شكرا", "صباح", "مساء", "الخير", "أهلا", "وسهلا",
        "لوحة", "مفاتيح", "ذكية", "برمجة", "تطبيق", "أندرويد", "كتابة", "نص", "رسالة", "قائمة"
    )

    // Common English typo corrections
    private val commonTypoMap = mapOf(
        "teh" to "the",
        "adn" to "and",
        "taht" to "that",
        "thier" to "their",
        "waht" to "what",
        "becuase" to "because",
        "woudl" to "would",
        "recieve" to "receive",
        "seperate" to "separate",
        "untill" to "until"
    )

    // Dynamic user-learned words (cleared upon user request)
    private val userLearnedWords = mutableSetOf<String>()

    fun getSuggestions(currentWord: String, isArabic: Boolean): List<String> {
        val query = currentWord.trim()
        if (query.isEmpty()) return emptyList()

        val results = mutableListOf<String>()

        if (!isArabic) {
            val lower = query.lowercase(Locale.ROOT)

            // 1. Check direct autocorrect mapping
            commonTypoMap[lower]?.let { corrected ->
                results.add(corrected)
            }

            // 2. Learned words prefix match
            userLearnedWords.filter { it.startsWith(lower) && it != lower }
                .take(2)
                .forEach { if (!results.contains(it)) results.add(it) }

            // 3. Static dictionary prefix match
            englishWords.filter { it.startsWith(lower) && it != lower }
                .take(3 - results.size)
                .forEach { if (!results.contains(it)) results.add(it) }

            // If query is an exact match or unknown, offer capitalized option
            if (results.isEmpty() && query.length > 1) {
                results.add(query.replaceFirstChar { it.uppercase() })
            }
        } else {
            // Arabic prefix match
            arabicWords.filter { it.startsWith(query) && it != query }
                .take(3)
                .forEach { results.add(it) }

            userLearnedWords.filter { it.startsWith(query) && it != query }
                .take(3 - results.size)
                .forEach { if (!results.contains(it)) results.add(it) }
        }

        return results.take(3)
    }

    /**
     * Learn a typed word safely (never call in password or sensitive fields).
     */
    fun learnWord(word: String, isSensitive: Boolean) {
        if (isSensitive) return
        val clean = word.trim()
        if (clean.length in 3..25 && !clean.contains("@") && !clean.contains("http")) {
            userLearnedWords.add(clean.lowercase(Locale.ROOT))
        }
    }

    /**
     * Completely wipe learned words.
     */
    fun clearLearnedHistory() {
        userLearnedWords.clear()
    }
}
