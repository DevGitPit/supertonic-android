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
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convert(longVal)
        }
        val s = d.toString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convert(parts[0].toLong())
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
        val longVal = d.toLong()
        if (d == longVal.toDouble()) {
            return convertHindi(longVal)
        }
        val s = d.toString()
        val parts = s.split(".")
        if (parts.size == 2) {
            val whole = convertHindi(parts[0].toLong())
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
}

