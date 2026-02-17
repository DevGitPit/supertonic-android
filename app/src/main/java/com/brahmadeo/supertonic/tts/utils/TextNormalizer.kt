package com.brahmadeo.supertonic.tts.utils

import java.util.regex.Pattern

/**
 * Enhanced TextNormalizer with comprehensive rule set
 * Handles currencies, numbers, abbreviations, and more for natural TTS
 */
class TextNormalizer {
    private val currencyNormalizer = CurrencyNormalizer()
    
    data class Rule(val pattern: Pattern, val replacement: (java.util.regex.Matcher) -> String)
    private val rules: List<Rule> = initializeRules()

    private fun initializeRules(): List<Rule> {
        val rulesList = mutableListOf<Rule>()
        
        fun addStr(regex: String, replacement: String) {
            rulesList.add(Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE)) { replacement })
        }
        
        fun addLambda(regex: String, replacement: (java.util.regex.Matcher) -> String) {
            rulesList.add(Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement))
        }

        // QUOTE PUNCTUATION SPACING (Model Stability)
        // Adds space between double quote and punctuation (".) -> (" .) to prevent audio glitches
        addLambda("([\"”])([.!?])") { m ->
            "${m.group(1)} ${m.group(2)}"
        }

        // PARENTHESES SPACING (Audio Fix)
        // Adds space inside parentheses to fix tokenization artifacts
        addLambda("\\(([^)]+)\\)") { m ->
            "( ${m.group(1)} )"
        }

        // RANGE NORMALIZATION (e.g. 10-15 years -> 10 to 15 years)
        // Matches digits separated by hyphen (-), en dash (–), or em dash (—)
        addLambda("\\b(\\d+)\\s*[-–—]\\s*(\\d+)\\b") { m ->
            "${m.group(1)} to ${m.group(2)}"
        }

        // EM DASH NORMALIZATION (Priority: High)
        // Replace em dashes with comma to prevent hard pauses/sentence splitting
        addStr("\\s*[—]\\s*", ", ")

        // EMERGENCY NUMBERS (Priority: Highest)
        addStr("\\b911\\b", "nine one one")
        addLambda("\\b(999|112|000)\\b") { m -> 
            val num = m.group(1) ?: ""
            num.toCharArray().joinToString(" ") 
        }

        // MEASUREMENTS
        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*m\\b(?=[^a-zA-Z]|$)") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            if (amount == "1") "1 meter" else "$valStr meters"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(km|mi)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = if (unit == "km") "kilometers" else "miles"
            "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(kph|mph|kmh|km/h|m/s)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = when (unit) {
                "kph", "kmh", "km/h" -> "kilometers per hour"
                "mph" -> "miles per hour"
                "m/s" -> "meters per second"
                else -> unit
            }
            "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)(kg|g|lb|lbs)\\b") { m ->
            val amount = m.group(1) ?: ""
            val unit = m.group(2)?.lowercase() ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            val fullUnit = when (unit) {
                "kg" -> "kilograms"
                "g" -> "grams"
                "lb", "lbs" -> "pounds"
                else -> unit
            }
            if (amount == "1") "1 ${fullUnit.trimEnd('s')}" else "$valStr $fullUnit"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)h\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            if (amount == "1") "1 hour" else "$valStr hours"
        }

        // LARGE NUMBERS (Non-currency)
        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*(?:M|mn)\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr million"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)\\s*(?:B|bn)\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr billion"
        }

        addLambda("\\b(\\d+(?:\\.\\d+)?)tn\\b") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr trillion"
        }

        // PERCENTAGES
        addLambda("\\b(\\d+(?:\\.\\d+)?)%") { m ->
            val amount = m.group(1) ?: ""
            val valStr = if (amount.contains(".")) amount.replace(".", " point ") else amount
            "$valStr percent"
        }

        // ORDINALS
        addLambda("\\b(\\d+)(st|nd|rd|th)\\b") { m ->
            val num = m.group(1)?.toIntOrNull() ?: 0
            numberToOrdinal(num)
        }

        // YEARS
        // Rule: 2000-2009 (Priority over general split)
        addLambda("\\b200(\\d)\\b") { m ->
            val digit = m.group(1) ?: "0"
            if (digit == "0") "two thousand" else "two thousand $digit"
        }

        // Rule: 1900-1909
        addLambda("\\b190(\\d)\\b") { m ->
            val digit = m.group(1) ?: "0"
            if (digit == "0") "nineteen hundred" else "nineteen oh $digit"
        }

        // YEARS (Split 4 digit years starting with 19 or 20)
        addLambda("\\b(19|20)(\\d{2})\\b(?!s)") { m ->
            "${m.group(1)} ${m.group(2)}"
        }

        // TITLES
        addLambda("\\b(Prof|Dr|Mr|Mrs|Ms)\\.\\s+") { m ->
            when (m.group(1)) {
                "Prof" -> "Professor "
                "Dr" -> "Doctor "
                "Mr" -> "Mister "
                "Mrs" -> "Missus "
                "Ms" -> "Miss "
                else -> m.group(0) ?: ""
            }
        }

        // ABBREVIATIONS
        addLambda("\\b(approx|vs|etc)\\.\\b") { m ->
            when (m.group(1)?.lowercase()) {
                "approx" -> "approximately"
                "vs" -> "versus"
                "etc" -> "et cetera"
                else -> m.group(0) ?: ""
            }
        }

        return rulesList
    }

    private fun numberToOrdinal(num: Int): String {
        val ordinals = mapOf(
            1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth",
            6 to "sixth", 7 to "seventh", 8 to "eighth", 9 to "ninth", 10 to "tenth",
            11 to "eleventh", 12 to "twelfth", 13 to "thirteenth", 14 to "fourteenth",
            15 to "fifteenth", 16 to "sixteenth", 17 to "seventeenth", 18 to "eighteenth",
            19 to "nineteenth", 20 to "twentieth"
        )
        
        if (ordinals.containsKey(num)) return ordinals[num]!!
        
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val ones = arrayOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth")
        
        if (num < 100) {
            val tenDigit = num / 10
            val oneDigit = num % 10
            if (oneDigit == 0) return "${tens[tenDigit]}th"
            return "${tens[tenDigit]} ${ones[oneDigit]}"
        }
        
        val lastTwo = num % 100
        if (lastTwo in 11..13) return "${num}th"
        
        val lastDigit = num % 10
        return when (lastDigit) {
            1 -> "${num}st"
            2 -> "${num}nd"
            3 -> "${num}rd"
            else -> "${num}th"
        }
    }

    fun normalize(text: String, lang: String = "en"): String {
        // EARLY EXIT for non-English
        // We skip Lexicon and all normalization for non-English languages for now
        // to prevent English-specific rules from mangling other scripts (e.g. Korean).
        if (!lang.lowercase().startsWith("en")) {
            return text
        }

        // Step -1: Apply User Lexicon
        val lexText = LexiconManager.apply(text)

        // Step 0: Fix smushed text from webpage layouts
        // Fix smushed sentences: lowercase char, period, uppercase char (reserved.Reuse)
        val smushedSentencePattern = Pattern.compile("([a-z])\\.([A-Z])")
        var fixedText = smushedSentencePattern.matcher(lexText).replaceAll("$1. $2")
        
        // Fix smushed words: lowercase char, uppercase char (economyIMF)
        val smushedWordPattern1 = Pattern.compile("([a-z])([A-Z])")
        fixedText = smushedWordPattern1.matcher(fixedText).replaceAll("$1 $2")
        
        // Fix smushed words: Uppercase followed by Uppercase+Lowercase (FTNews)
        val smushedWordPattern2 = Pattern.compile("([A-Z])([A-Z][a-z])")
        fixedText = smushedWordPattern2.matcher(fixedText).replaceAll("$1 $2")

        // Fix letter-number merges (Published8 -> Published 8)
        val letterNumberPattern = Pattern.compile("([a-zA-Z])(\\d)")
        fixedText = letterNumberPattern.matcher(fixedText).replaceAll("$1 $2")

        // Step 1: Currency
        var normalized = currencyNormalizer.normalize(fixedText)
        
        // Step 2: Other rules
        for (rule in rules) {
            val matcher = rule.pattern.matcher(normalized)
            val sb = StringBuffer()
            while (matcher.find()) {
                val replacement = rule.replacement(matcher).replace("\\", "\\\\").replace("$", "\\$")
                matcher.appendReplacement(sb, replacement)
            }
            matcher.appendTail(sb)
            normalized = sb.toString()
        }

        // Step 3: Convert remaining numbers to words (CRITICAL for C++ Engine)
        // Matches integers and decimals (e.g. "300000" -> "three hundred thousand")
        val numberPattern = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
        val matcher = numberPattern.matcher(normalized)
        val sb = StringBuffer()
        while (matcher.find()) {
            val numStr = matcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertDouble(numStr.toDouble())
                } else {
                    NumberUtils.convert(numStr.toLong())
                }
                matcher.appendReplacement(sb, replacement)
            } catch (e: Exception) {
                // If number is too large for Long, keep it as digits (or implement BigInt logic if needed)
                // For TTS, massive numbers usually read digit-by-digit anyway
                matcher.appendReplacement(sb, numStr)
            }
        }
        matcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }

    fun splitIntoSentences(text: String, lang: String = "en"): List<String> {
        val l = lang.lowercase()
        val isEnglish = l.startsWith("en")
        val isRomance = l.startsWith("es") || l.startsWith("fr") || l.startsWith("pt")

        val abbreviations = listOf(
            "Mr.", "Mrs.", "Dr.", "Ms.", "Prof.", "Sr.", "Jr.", 
            "etc.", "vs.", "e.g.", "i.e.",
            "Jan.", "Feb.", "Mar.", "Apr.", "May.", "Jun.", 
            "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.",
            "U.S.", "U.K.", "E.U."
        )
        var protectedText = text
        
        abbreviations.forEachIndexed { index, abbr ->
            val placeholder = "__ABBR${index}__"
            val pattern = Pattern.compile("\\b" + Pattern.quote(abbr), Pattern.CASE_INSENSITIVE)
            protectedText = pattern.matcher(protectedText).replaceAll(placeholder)
        }
        
        // Split by:
        // 1. Punctuation (.!?) followed by optional quote and space and Capital/Number/Unicode Letter
        // 2. Semi-colon followed by space
        // ADDED: Romance punctuation support (¿, ¡)
        val pattern = Pattern.compile("(?<=[.!?¿¡]['\"”’]?)\\s+(?=['\"“‘]?[\\p{L}\\d])|(?<=[;])\\s+")
        val rawSentences = protectedText.split(pattern)
        
        val refinedSentences = mutableListOf<String>()
        val MAX_LENGTH = 300

        for (raw in rawSentences) {
            if (raw.length <= MAX_LENGTH) {
                refinedSentences.add(raw)
            } else {
                // Split long sentences by comma if they are too long
                val subParts = raw.split(Pattern.compile("(?<=,)\\s+"))
                var currentPart = StringBuilder()
                
                for (part in subParts) {
                    if (currentPart.length + part.length < MAX_LENGTH) {
                        if (currentPart.isNotEmpty()) currentPart.append(" ")
                        currentPart.append(part)
                    } else {
                        if (currentPart.isNotEmpty()) {
                            // Fix audio cutoff: If breaking at a comma or semi-colon, strip it
                            // to prevent hard stops or artifacts.
                            if (currentPart.endsWith(",") || currentPart.endsWith(";")) {
                                currentPart.deleteCharAt(currentPart.length - 1)
                            }
                            refinedSentences.add(currentPart.toString())
                            currentPart.clear()
                        }
                        currentPart.append(part)
                    }
                }
                if (currentPart.isNotEmpty()) {
                    refinedSentences.add(currentPart.toString())
                }
            }
        }

        val processedSentences = refinedSentences.map { sentence ->
            var restored = sentence
            abbreviations.forEachIndexed { index, abbr ->
                val placeholder = "__ABBR${index}__"
                restored = restored.replace(placeholder, abbr, ignoreCase = true)
            }
            restored.trim()
        }.filter { it.isNotEmpty() }

        // Chunking Logic: Accumulate sentences up to MAX_LENGTH (300)
        val chunkedSentences = mutableListOf<String>()
        var currentChunk = StringBuilder()
        val CHUNK_LIMIT = 300

        var i = 0
        while (i < processedSentences.size) {
            var sentence = processedSentences[i]
            
            // Volatile/Punctuation Fix:
            // Always insert space before !, ?, ,, ; to stabilize audio
            // Restricted to English/Romance to avoid mangling other scripts
            if (isEnglish || isRomance) {
                sentence = sentence.replaceFirst(Regex("([!?,;¿¡])(['\"”’]?)\\s*$"), " $1$2")
            }

            // HANDLE STABLE SENTENCE (Standard Accumulation)
            if (currentChunk.length + sentence.length + 1 <= CHUNK_LIMIT) {
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append(" ")
                }
                currentChunk.append(sentence)
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunkedSentences.add(currentChunk.toString())
                    currentChunk.clear()
                }
                // If a single sentence is huge, add it directly
                if (sentence.length > CHUNK_LIMIT) {
                    chunkedSentences.add(sentence)
                } else {
                    currentChunk.append(sentence)
                }
            }
            i++
        }

        if (currentChunk.isNotEmpty()) {
            chunkedSentences.add(currentChunk.toString())
        }

        return chunkedSentences
    }

        if (currentChunk.isNotEmpty()) {
            chunkedSentences.add(currentChunk.toString())
        }

        return chunkedSentences
    }
}
