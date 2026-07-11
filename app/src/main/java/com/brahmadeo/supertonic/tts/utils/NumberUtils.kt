package com.brahmadeo.supertonic.tts.utils

object NumberUtils {

    private val units = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen"
    )

    private val tens = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    fun convert(n: Long): String {
        if (n < 0) {
            if (n == Long.MIN_VALUE) {
                return "minus " + convert(-(n / 1000000000)) + " billion" + (" " + convert(-(n % 1000000000)))
            }
            return "minus " + convert(-n)
        }
        if (n == 0L) {
            return "zero"
        }
        if (n < 20) {
            return units[n.toInt()]
        }
        if (n < 100) {
            return tens[n.toInt() / 10] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
        }
        if (n < 1000) {
            return units[(n / 100).toInt()] + " hundred" + (if (n % 100 != 0L) " " + convert(n % 100) else "")
        }
        if (n < 1000000) {
            return convert(n / 1000) + " thousand" + (if (n % 1000 != 0L) " " + convert(n % 1000) else "")
        }
        if (n < 1000000000) {
            return convert(n / 1000000) + " million" + (if (n % 1000000 != 0L) " " + convert(n % 1000000) else "")
        }
        return convert(n / 1000000000) + " billion" + (if (n % 1000000000 != 0L) " " + convert(n % 1000000000) else "")
    }

    fun convertDouble(d: Double): String {
        if (d < 0) {
            return "minus " + convertDouble(-d)
        }
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convert(longVal)
        }
        val s = java.math.BigDecimal.valueOf(d).toPlainString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convert(parts[0].toLongOrNull() ?: longVal)
            val fraction = parts[1].map { 
                if (it.isDigit()) units[it.digitToInt()] else "" 
            }.joinToString(" ")
            return "$whole point $fraction"
        }
        return convert(longVal)
    }

    private val hindi0to99 = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस",
        "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
        "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
        "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
        "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
        "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ", "साठ",
        "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सरसठ", "अड़सठ", "उनहत्तर", "सत्तर",
        "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी", "अस्सी",
        "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी", "नब्बे",
        "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पंचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
    )

    private val BULGARIAN_HUNDREDS = arrayOf(
        "", "сто", "двеста", "триста", "четиристотин", "петстотин", "шестстотин", "седемстотин", "осемстотин", "деветстотин"
    )

    private val BULGARIAN_TENS = arrayOf(
        "", "", "двадесет", "тридесет", "четиридесет", "петдесет", "шестдесет", "седемдесет", "осемдесет", "деветдесет"
    )

    private val BULGARIAN_FRACTION_DIGITS = arrayOf(
        "нула", "едно", "две", "три", "четири", "пет", "шест", "седем", "осем", "девет"
    )

    private val GERMAN_0_TO_19 = arrayOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"
    )

    private val GERMAN_TENS = arrayOf(
        "", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig"
    )

    private val GERMAN_FRACTION_DIGITS = arrayOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"
    )

    fun convertHindi(n: Long): String {
        if (n < 0) {
            if (n == Long.MIN_VALUE) {
                return "माइनस " + convertHindi(-(n / 10000000)) + " करोड़" + (" " + convertHindi(-(n % 10000000)))
            }
            return "माइनस " + convertHindi(-n)
        }
        if (n == 0L) {
            return "शून्य"
        }
        if (n < 100) {
            return hindi0to99[n.toInt()]
        }
        
        val result = StringBuilder()
        var temp = n
        
        if (temp >= 10000000) {
            val crore = temp / 10000000
            result.append(convertHindi(crore)).append(" करोड़")
            temp %= 10000000
            if (temp > 0) result.append(" ")
        }
        
        if (temp >= 100000) {
            val lakh = temp / 100000
            result.append(convertHindi(lakh)).append(" लाख")
            temp %= 100000
            if (temp > 0) result.append(" ")
        }
        
        if (temp >= 1000) {
            val realThousand = temp / 1000
            result.append(convertHindi(realThousand)).append(" हज़ार")
            temp %= 1000
            if (temp > 0) result.append(" ")
        }
        
        if (temp >= 100) {
            val hundred = temp / 100
            result.append(convertHindi(hundred)).append(" सौ")
            temp %= 100
            if (temp > 0) result.append(" ")
        }
        
        if (temp > 0) {
            result.append(hindi0to99[temp.toInt()])
        }
        
        return result.toString().trim()
    }

    fun convertHindiDouble(d: Double): String {
        if (d < 0) {
            return "माइनस " + convertHindiDouble(-d)
        }
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convertHindi(longVal)
        }
        val s = java.math.BigDecimal.valueOf(d).toPlainString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convertHindi(parts[0].toLongOrNull() ?: longVal)
            val fraction = parts[1].map { 
                if (it.isDigit()) {
                    val digit = it.digitToInt()
                    hindi0to99[digit]
                } else "" 
            }.filter { it.isNotEmpty() }.joinToString(" ")
            return "$whole दशमलव $fraction"
        }
        return convertHindi(longVal)
    }

    fun convertBulgarian(n: Long): String {
        if (n < 0) {
            if (n == Long.MIN_VALUE) {
                return "минус " + convertBulgarian(-(n / 1000000000)) + " милиарда" + (" " + convertBulgarian(-(n % 1000000000)))
            }
            return "минус " + convertBulgarian(-n)
        }
        if (n == 0L) {
            return "нула"
        }
        return convertBulgarianInternal(n, "neut")
    }

    private fun convertBulgarianInternal(n: Long, gender: String): String {
        if (n < 20) {
            return when (n.toInt()) {
                1 -> when (gender) {
                    "masc" -> "един"
                    "fem" -> "една"
                    else -> "едно"
                }
                2 -> when (gender) {
                    "masc" -> "два"
                    else -> "две"
                }
                3 -> "три"
                4 -> "четири"
                5 -> "пет"
                6 -> "шест"
                7 -> "седем"
                8 -> "осем"
                9 -> "девет"
                10 -> "десет"
                11 -> "единадесет"
                12 -> "дванадесет"
                13 -> "тринадесет"
                14 -> "четиринадесет"
                15 -> "петнадесет"
                16 -> "шестнадесет"
                17 -> "седемнадесет"
                18 -> "осемнадесет"
                19 -> "деветнадесет"
                else -> ""
            }
        }
        val parts = mutableListOf<String>()
        val billions = n / 1000000000L
        val millions = (n % 1000000000L) / 1000000L
        val thousands = (n % 1000000L) / 1000L
        val hundreds = (n % 1000L) / 100L
        val remainder = n % 100L

        if (billions > 0) {
            if (billions == 1L) {
                parts.add("един милиард")
            } else {
                parts.add(convertBulgarianInternal(billions, "masc") + " милиарда")
            }
        }
        if (millions > 0) {
            if (millions == 1L) {
                parts.add("един милион")
            } else {
                parts.add(convertBulgarianInternal(millions, "masc") + " милиона")
            }
        }
        if (thousands > 0) {
            if (thousands == 1L) {
                parts.add("хиляда")
            } else {
                parts.add(convertBulgarianInternal(thousands, "fem") + " хиляди")
            }
        }
        if (hundreds > 0) {
            parts.add(BULGARIAN_HUNDREDS[hundreds.toInt()])
        }
        if (remainder > 0) {
            if (remainder < 20) {
                parts.add(convertBulgarianInternal(remainder, gender))
            } else {
                parts.add(BULGARIAN_TENS[(remainder / 10).toInt()])
                val ones = remainder % 10
                if (ones > 0) {
                    parts.add(convertBulgarianInternal(ones, gender))
                }
            }
        }

        if (parts.isEmpty()) return ""
        if (parts.size == 1) return parts[0]
        val head = parts.subList(0, parts.size - 1).joinToString(" ")
        val tail = parts.last()
        return "$head и $tail"
    }

    fun convertBulgarianDouble(d: Double): String {
        if (d < 0) {
            return "минус " + convertBulgarianDouble(-d)
        }
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convertBulgarian(longVal)
        }
        val s = java.math.BigDecimal.valueOf(d).toPlainString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convertBulgarian(parts[0].toLongOrNull() ?: longVal)
            val fraction = parts[1].map {
                if (it.isDigit()) BULGARIAN_FRACTION_DIGITS[it.digitToInt()] else ""
            }.filter { it.isNotEmpty() }.joinToString(" ")
            return "$whole запетая $fraction"
        }
        return convertBulgarian(longVal)
    }

    fun convertGerman(n: Long): String {
        if (n < 0) {
            if (n == Long.MIN_VALUE) {
                return "minus " + convertGerman(-(n / 1000000000)) + " Milliarden" + (" " + convertGerman(-(n % 1000000000)))
            }
            return "minus " + convertGerman(-n)
        }
        if (n == 0L) {
            return "null"
        }
        if (n < 100) {
            return convertGermanUnder100(n.toInt())
        }
        if (n < 1000) {
            val hundredVal = (n / 100).toInt()
            val rem = (n % 100).toInt()
            val hundredStr = (if (hundredVal == 1) "ein" else convertGermanUnder100(hundredVal)) + "hundert"
            return if (rem == 0) hundredStr else hundredStr + convertGermanUnder100(rem)
        }
        if (n < 1000000) {
            val thousandVal = n / 1000
            val rem = n % 1000
            val thousandStr = (if (thousandVal == 1L) "ein" else convertGerman(thousandVal).let { if (it.endsWith("eins")) it.dropLast(1) else it }) + "tausend"
            return if (rem == 0L) thousandStr else thousandStr + convertGerman(rem)
        }
        if (n < 1000000000) {
            val millionVal = n / 1000000
            val rem = n % 1000000
            val millionStr = if (millionVal == 1L) "eine Million" else convertGerman(millionVal).let { if (it.endsWith("eins")) it.dropLast(1) else it } + " Millionen"
            return if (rem == 0L) millionStr else "$millionStr " + convertGerman(rem)
        }
        val billionVal = n / 1000000000
        val rem = n % 1000000000
        val billionStr = if (billionVal == 1L) "eine Milliarde" else convertGerman(billionVal).let { if (it.endsWith("eins")) it.dropLast(1) else it } + " Milliarden"
        return if (rem == 0L) billionStr else "$billionStr " + convertGerman(rem)
    }

    private fun convertGermanUnder100(n: Int): String {
        if (n < 20) {
            return GERMAN_0_TO_19[n]
        }
        val tensVal = n / 10
        val onesVal = n % 10
        if (onesVal == 0) {
            return GERMAN_TENS[tensVal]
        }
        val onesStr = if (onesVal == 1) "ein" else GERMAN_0_TO_19[onesVal]
        return onesStr + "und" + GERMAN_TENS[tensVal]
    }

    fun convertGermanDouble(d: Double): String {
        if (d < 0) {
            return "minus " + convertGermanDouble(-d)
        }
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convertGerman(longVal)
        }
        val s = java.math.BigDecimal.valueOf(d).toPlainString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convertGerman(parts[0].toLongOrNull() ?: longVal)
            val fraction = parts[1].map {
                if (it.isDigit()) GERMAN_FRACTION_DIGITS[it.digitToInt()] else ""
            }.filter { it.isNotEmpty() }.joinToString(" ")
            return "$whole Komma $fraction"
        }
        return convertGerman(longVal)
    }
}

