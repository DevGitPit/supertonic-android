package com.brahmadeo.supertonic.tts.utils

import java.util.regex.Pattern
import com.brahmadeo.supertonic.tts.SupertonicTTS

/**
 * Enhanced TextNormalizer with comprehensive rule set
 * Handles currencies, numbers, abbreviations, and more for natural TTS
 */
class TextNormalizer {
    companion object {
        private val NAME_PREFIX_REGEX = Regex("\\b(Mc|Mac|Fitz)([A-Z])")
        
        // Hoisted patterns from normalize() and normalizeHindi()
        private val SMUSHED_SENTENCE_PATTERN = Pattern.compile("([a-z])\\.([A-Z])")
        private val SMUSHED_WORD_PATTERN_1 = Pattern.compile("([a-z])([A-Z])")
        private val SMUSHED_WORD_PATTERN_2 = Pattern.compile("([A-Z])([A-Z][a-z])")
        private val LETTER_NUMBER_PATTERN = Pattern.compile("([a-zA-Z])(\\d)")
        private val NUMBER_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
        
        // Hindi specific patterns
        private val COMMA_PATTERN = Pattern.compile("(?<=\\d),(?=\\d)")
        private val RANGE_PATTERN = Pattern.compile("\\b(\\d+)\\s*[-–—]\\s*(\\d+)\\b")
        private val CURRENCY_PATTERN = Pattern.compile("(?:\\bINR|₹)\\s*(\\d+(?:\\.\\d+)?)\\b")
        private val PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%")

        // Bulgarian & German specific patterns
        private val THOUSANDS_DOT_PATTERN = Pattern.compile("(?<=\\d)\\.(?=\\d{3}(?:\\D|$))")
        private val DECIMAL_COMMA_PATTERN = Pattern.compile("(?<=\\d),(?=\\d)")

        private val NEGATIVE_SIGN_PATTERN = Pattern.compile("(?<=\\s|^|\\()[-–—−](?=\\d)")

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

        // Rule: 1500-1909
        addLambda("\\b(1[5-9])0(\\d)\\b") { m ->
            val century = m.group(1)?.toLongOrNull() ?: 19L
            val centuryStr = NumberUtils.convert(century)
            val digit = m.group(2) ?: "0"
            if (digit == "0") "$centuryStr hundred" else "$centuryStr oh $digit"
        }

        // YEARS (Split 4 digit years starting with 15-20)
        addLambda("\\b(1[5-9]|20)(\\d{2})\\b(?!s)") { m ->
            "${m.group(1)} ${m.group(2)}"
        }

        // CENTURIES (e.g. 1800s -> eighteen hundreds)
        addLambda("\\b(1[5-9])00s\\b") { m ->
            val century = m.group(1)?.toLongOrNull() ?: 19L
            val centuryStr = NumberUtils.convert(century)
            "$centuryStr hundreds"
        }
        addStr("\\b2000s\\b", "two thousands")

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
        private val currencyNormalizer = CurrencyNormalizer()
    }

    fun normalize(text: String, lang: String = "en", isAdvancedEnabled: Boolean = false): String {
        val lowerLang = lang.lowercase()
        
        // Remove soft hyphens (\u00AD) globally
        val cleanText = text.replace("\u00AD", "")
        
        // 1. Lexicon applies to all languages except Korean
        val processedText = if (lowerLang != "ko") {
            LexiconManager.apply(cleanText)
        } else {
            cleanText
        }

        if (lowerLang.startsWith("hi")) {
            return normalizeHindi(processedText)
        }

        if (lowerLang.startsWith("bg")) {
            return normalizeBulgarian(processedText)
        }

        if (lowerLang.startsWith("de")) {
            return normalizeGerman(processedText)
        }

        // 2. Determine if we should apply English-style normalization rules
        // Currently: Always for English, or if toggle is on for Romance languages
        val isRomance = lowerLang.startsWith("fr") || lowerLang.startsWith("es") || lowerLang.startsWith("pt")
        val shouldNormalize = lowerLang.startsWith("en") || (isRomance && isAdvancedEnabled)

        if (!shouldNormalize) {
            return processedText
        }

        // Clean up negative signs: "-5" -> "minus 5"
        val negativeNormalizedText = NEGATIVE_SIGN_PATTERN.matcher(processedText).replaceAll("minus ")

        // Normalize names like McDonald, MacDonald, FitzGerald to Mcdonald, Macdonald, Fitzgerald
        // to prevent them from being split by SMUSHED_WORD_PATTERN_1 and to ensure correct pronunciation.
        val preparedText = NAME_PREFIX_REGEX.replace(negativeNormalizedText) { matchResult ->
            matchResult.groupValues[1] + matchResult.groupValues[2][0].lowercaseChar()
        }

        // Step 0: Fix smushed text from webpage layouts
        // Fix smushed sentences: lowercase char, period, uppercase char (reserved.Reuse)
        var fixedText = SMUSHED_SENTENCE_PATTERN.matcher(preparedText).replaceAll("$1. $2")
        
        // Fix smushed words: lowercase char, uppercase char (economyIMF)
        fixedText = SMUSHED_WORD_PATTERN_1.matcher(fixedText).replaceAll("$1 $2")
        
        // Fix smushed words: Uppercase followed by Uppercase+Lowercase (FTNews)
        fixedText = SMUSHED_WORD_PATTERN_2.matcher(fixedText).replaceAll("$1 $2")

        // Fix letter-number merges (Published8 -> Published 8)
        fixedText = LETTER_NUMBER_PATTERN.matcher(fixedText).replaceAll("$1 $2")

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
        val matcher = NUMBER_PATTERN.matcher(normalized)
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
            } catch (_: Exception) {
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
        return SupertonicTTS.chunkText(text, lang)
    }

    private fun normalizeHindi(text: String): String {
        // 0. Clean up negative signs
        var normalized = NEGATIVE_SIGN_PATTERN.matcher(text).replaceAll("माइनस ")

        // 1. Convert Devanagari digits (०-९) to Latin digits (0-9)
        normalized = convertDevanagariDigits(normalized)

        // 2. Clean up commas in numbers (e.g. "1,50,000" -> "150000")
        normalized = COMMA_PATTERN.matcher(normalized).replaceAll("")

        // 3. Hindi Range Normalization (e.g., "10-15" -> "10 से 15")
        val rangeMatcher = RANGE_PATTERN.matcher(normalized)
        val rangeSb = StringBuffer()
        while (rangeMatcher.find()) {
            val replacement = "${rangeMatcher.group(1)} से ${rangeMatcher.group(2)}"
            rangeMatcher.appendReplacement(rangeSb, replacement)
        }
        rangeMatcher.appendTail(rangeSb)
        normalized = rangeSb.toString()

        // 4. Currency symbols (e.g. "₹500" or "INR 500" -> "500 रुपये")
        val currencyMatcher = CURRENCY_PATTERN.matcher(normalized)
        val currencySb = StringBuffer()
        while (currencyMatcher.find()) {
            val amount = currencyMatcher.group(1) ?: ""
            val replacement = "$amount रुपये"
            currencyMatcher.appendReplacement(currencySb, replacement)
        }
        currencyMatcher.appendTail(currencySb)
        normalized = currencySb.toString()

        // 5. Percentages (e.g. "5%" -> "5 प्रतिशत")
        val percentMatcher = PERCENT_PATTERN.matcher(normalized)
        val percentSb = StringBuffer()
        while (percentMatcher.find()) {
            val amount = percentMatcher.group(1) ?: ""
            val replacement = "$amount प्रतिशत"
            percentMatcher.appendReplacement(percentSb, replacement)
        }
        percentMatcher.appendTail(percentSb)
        normalized = percentSb.toString()

        // 6. Convert remaining numbers to words (CRITICAL for C++ Engine)
        val numberMatcher = NUMBER_PATTERN.matcher(normalized)
        val sb = StringBuffer()
        while (numberMatcher.find()) {
            val numStr = numberMatcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertHindiDouble(numStr.toDouble())
                } else {
                    NumberUtils.convertHindi(numStr.toLong())
                }
                numberMatcher.appendReplacement(sb, replacement)
            } catch (_: Exception) {
                numberMatcher.appendReplacement(sb, numStr)
            }
        }
        numberMatcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }

    private fun convertDevanagariDigits(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            val replaced = when (char) {
                '०' -> '0'
                '१' -> '1'
                '२' -> '2'
                '३' -> '3'
                '४' -> '4'
                '५' -> '5'
                '६' -> '6'
                '७' -> '7'
                '८' -> '8'
                '९' -> '9'
                else -> char
            }
            sb.append(replaced)
        }
        return sb.toString()
    }

    private fun normalizeBulgarian(text: String): String {
        // 0. Clean up negative signs
        var normalized = NEGATIVE_SIGN_PATTERN.matcher(text).replaceAll("минус ")

        // 1. Clean up thousands dot separators
        normalized = THOUSANDS_DOT_PATTERN.matcher(normalized).replaceAll("")

        // 2. Clean up decimal commas
        normalized = DECIMAL_COMMA_PATTERN.matcher(normalized).replaceAll(".")

        // 3. Range normalization: "10-15" -> "10 до 15"
        val rangeMatcher = RANGE_PATTERN.matcher(normalized)
        val rangeSb = StringBuffer()
        while (rangeMatcher.find()) {
            val replacement = "${rangeMatcher.group(1)} до ${rangeMatcher.group(2)}"
            rangeMatcher.appendReplacement(rangeSb, replacement)
        }
        rangeMatcher.appendTail(rangeSb)
        normalized = rangeSb.toString()

        // 4. Percentages: "5%" -> "5 процента", "21%" -> "21 процент"
        val percentMatcher = PERCENT_PATTERN.matcher(normalized)
        val percentSb = StringBuffer()
        while (percentMatcher.find()) {
            val amountStr = percentMatcher.group(1) ?: ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val amountLong = amount.toLong()
            val isInt = amount == amountLong.toDouble()
            val suffix = if (isInt && amountLong % 10L == 1L && amountLong % 100L != 11L) {
                "процент"
            } else {
                "процента"
            }
            val word = if (isInt) {
                NumberUtils.convertBulgarian(amountLong)
            } else {
                NumberUtils.convertBulgarianDouble(amount)
            }
            val adjustedWord = if (suffix == "процент") {
                if (word == "едно") "един"
                else if (word.endsWith(" едно")) word.substring(0, word.length - 4) + "един"
                else word
            } else {
                if (word == "две") "два"
                else if (word.endsWith(" две")) word.substring(0, word.length - 3) + "два"
                else word
            }
            val replacement = "$adjustedWord $suffix"
            percentMatcher.appendReplacement(percentSb, replacement)
        }
        percentMatcher.appendTail(percentSb)
        normalized = percentSb.toString()

        // 5. Convert remaining numbers to words
        val numberMatcher = NUMBER_PATTERN.matcher(normalized)
        val sb = StringBuffer()
        while (numberMatcher.find()) {
            val numStr = numberMatcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertBulgarianDouble(numStr.toDouble())
                } else {
                    NumberUtils.convertBulgarian(numStr.toLong())
                }
                numberMatcher.appendReplacement(sb, replacement)
            } catch (_: Exception) {
                numberMatcher.appendReplacement(sb, numStr)
            }
        }
        numberMatcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }

    private fun normalizeGerman(text: String): String {
        // 0. Clean up negative signs
        var normalized = NEGATIVE_SIGN_PATTERN.matcher(text).replaceAll("minus ")

        // 1. Clean up thousands dot separators
        normalized = THOUSANDS_DOT_PATTERN.matcher(normalized).replaceAll("")

        // 2. Clean up decimal commas
        normalized = DECIMAL_COMMA_PATTERN.matcher(normalized).replaceAll(".")

        // 3. Range normalization: "10-15" -> "10 bis 15"
        val rangeMatcher = RANGE_PATTERN.matcher(normalized)
        val rangeSb = StringBuffer()
        while (rangeMatcher.find()) {
            val replacement = "${rangeMatcher.group(1)} bis ${rangeMatcher.group(2)}"
            rangeMatcher.appendReplacement(rangeSb, replacement)
        }
        rangeMatcher.appendTail(rangeSb)
        normalized = rangeSb.toString()

        // 4. Percentages: "5%" -> "5 Prozent"
        val percentMatcher = PERCENT_PATTERN.matcher(normalized)
        val percentSb = StringBuffer()
        while (percentMatcher.find()) {
            val amountStr = percentMatcher.group(1) ?: ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val amountLong = amount.toLong()
            val isInt = amount == amountLong.toDouble()
            val word = if (isInt) {
                NumberUtils.convertGerman(amountLong)
            } else {
                NumberUtils.convertGermanDouble(amount)
            }
            val adjustedWord = if (word.endsWith("eins")) word.dropLast(1) else word
            val replacement = "$adjustedWord Prozent"
            percentMatcher.appendReplacement(percentSb, replacement)
        }
        percentMatcher.appendTail(percentSb)
        normalized = percentSb.toString()

        // 5. Convert remaining numbers to words
        val numberMatcher = NUMBER_PATTERN.matcher(normalized)
        val sb = StringBuffer()
        while (numberMatcher.find()) {
            val numStr = numberMatcher.group(1) ?: ""
            try {
                val replacement = if (numStr.contains(".")) {
                    NumberUtils.convertGermanDouble(numStr.toDouble())
                } else {
                    NumberUtils.convertGerman(numStr.toLong())
                }
                numberMatcher.appendReplacement(sb, replacement)
            } catch (_: Exception) {
                numberMatcher.appendReplacement(sb, numStr)
            }
        }
        numberMatcher.appendTail(sb)
        normalized = sb.toString()

        return normalized
    }
}
