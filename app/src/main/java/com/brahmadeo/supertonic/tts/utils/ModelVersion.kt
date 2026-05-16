package com.brahmadeo.supertonic.tts.utils

import com.brahmadeo.supertonic.tts.R
import java.util.Locale

/**
 * Single source of truth for model versions, their supported languages, and the locale/ISO codes
 * exposed to the Android TTS framework. Any place that needs to know "which languages does model X
 * support" or "what locale does language code Y map to" should consult this enum or the shared
 * maps below.
 */
enum class ModelVersion(
    val dirName: String,
    val baseUrl: String,
    val supportedLangs: Set<String>
) {
    V1(
        dirName = "v1",
        baseUrl = "https://huggingface.co/Supertone/supertonic/resolve/main",
        supportedLangs = setOf("en")
    ),
    V2(
        dirName = "v2",
        baseUrl = "https://huggingface.co/Supertone/supertonic-2/resolve/main",
        supportedLangs = setOf("ko", "es", "pt", "fr")
    ),
    V3(
        dirName = "v3",
        baseUrl = "https://huggingface.co/Supertone/supertonic-3/resolve/main",
        supportedLangs = setOf(
            "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
            "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
            "sk", "sl", "es", "sv", "tr", "uk", "vi"
        )
    );

    companion object {
        /** Resolves the model version to use given the user's saved language and readiness state. */
        fun resolve(savedLang: String, isV2Ready: Boolean, isV3Ready: Boolean): ModelVersion = when {
            savedLang == "en" -> V1
            isV3Ready -> V3
            isV2Ready && V2.supportedLangs.contains(savedLang) -> V2
            else -> V3
        }

        fun fromDirName(dirName: String): ModelVersion? = entries.firstOrNull { it.dirName == dirName }

        /**
         * BCP-47 locale per language code. Used by Android TTS Voice() construction and as a
         * canonical mapping when reporting available locales.
         */
        val LANG_LOCALES: Map<String, Locale> = mapOf(
            "ar" to Locale.forLanguageTag("ar-SA"),
            "bg" to Locale.forLanguageTag("bg-BG"),
            "hr" to Locale.forLanguageTag("hr-HR"),
            "cs" to Locale.forLanguageTag("cs-CZ"),
            "da" to Locale.forLanguageTag("da-DK"),
            "nl" to Locale.forLanguageTag("nl-NL"),
            "en" to Locale.US,
            "et" to Locale.forLanguageTag("et-EE"),
            "fi" to Locale.forLanguageTag("fi-FI"),
            "fr" to Locale.forLanguageTag("fr-FR"),
            "de" to Locale.forLanguageTag("de-DE"),
            "el" to Locale.forLanguageTag("el-GR"),
            "hi" to Locale.forLanguageTag("hi-IN"),
            "hu" to Locale.forLanguageTag("hu-HU"),
            "id" to Locale.forLanguageTag("id-ID"),
            "it" to Locale.forLanguageTag("it-IT"),
            "ja" to Locale.JAPAN,
            "ko" to Locale.KOREA,
            "lv" to Locale.forLanguageTag("lv-LV"),
            "lt" to Locale.forLanguageTag("lt-LT"),
            "pl" to Locale.forLanguageTag("pl-PL"),
            "pt" to Locale.forLanguageTag("pt-PT"),
            "ro" to Locale.forLanguageTag("ro-RO"),
            "ru" to Locale.forLanguageTag("ru-RU"),
            "sk" to Locale.forLanguageTag("sk-SK"),
            "sl" to Locale.forLanguageTag("sl-SI"),
            "es" to Locale.forLanguageTag("es-ES"),
            "sv" to Locale.forLanguageTag("sv-SE"),
            "tr" to Locale.forLanguageTag("tr-TR"),
            "uk" to Locale.forLanguageTag("uk-UA"),
            "vi" to Locale.forLanguageTag("vi-VN"),
        )

        /** ISO 639-2 alpha-3 plus alpha-3 country code, returned by TextToSpeechService.onGetLanguage(). */
        val LANG_ISO3: Map<String, Pair<String, String>> = mapOf(
            "en" to ("eng" to "USA"),
            "ar" to ("ara" to "SAU"),
            "bg" to ("bul" to "BGR"),
            "hr" to ("hrv" to "HRV"),
            "cs" to ("ces" to "CZE"),
            "da" to ("dan" to "DNK"),
            "nl" to ("nld" to "NLD"),
            "et" to ("est" to "EST"),
            "fi" to ("fin" to "FIN"),
            "fr" to ("fra" to "FRA"),
            "de" to ("deu" to "DEU"),
            "el" to ("ell" to "GRC"),
            "hi" to ("hin" to "IND"),
            "hu" to ("hun" to "HUN"),
            "id" to ("ind" to "IDN"),
            "it" to ("ita" to "ITA"),
            "ja" to ("jpn" to "JPN"),
            "ko" to ("kor" to "KOR"),
            "lv" to ("lav" to "LVA"),
            "lt" to ("lit" to "LTU"),
            "pl" to ("pol" to "POL"),
            "pt" to ("por" to "PRT"),
            "ro" to ("ron" to "ROU"),
            "ru" to ("rus" to "RUS"),
            "sk" to ("slk" to "SVK"),
            "sl" to ("slv" to "SVN"),
            "es" to ("spa" to "ESP"),
            "sv" to ("swe" to "SWE"),
            "tr" to ("tur" to "TUR"),
            "uk" to ("ukr" to "UKR"),
            "vi" to ("vie" to "VNM"),
        )

        /**
         * Maps a 2- or 3-letter language tag (any case) to the canonical 2-letter code used
         * internally. Slovak (slk/slo) must be matched before Slovenian (sl/slv) to avoid a
         * prefix collision.
         */
        fun normalize(lang: String?): String {
            if (lang == null) return "en"
            val l = lang.lowercase(Locale.ROOT)
            return when {
                l.startsWith("en") || l.startsWith("eng") -> "en"
                l.startsWith("ar") || l.startsWith("ara") -> "ar"
                l.startsWith("bg") || l.startsWith("bul") -> "bg"
                l.startsWith("hr") || l.startsWith("hrv") -> "hr"
                l.startsWith("cs") || l.startsWith("ces") || l.startsWith("cze") -> "cs"
                l.startsWith("da") || l.startsWith("dan") -> "da"
                l.startsWith("nl") || l.startsWith("nld") || l.startsWith("dut") -> "nl"
                l.startsWith("et") || l.startsWith("est") -> "et"
                l.startsWith("fi") || l.startsWith("fin") -> "fi"
                l.startsWith("fr") || l.startsWith("fra") || l.startsWith("fre") -> "fr"
                l.startsWith("de") || l.startsWith("deu") || l.startsWith("ger") -> "de"
                l.startsWith("el") || l.startsWith("ell") || l.startsWith("gre") -> "el"
                l.startsWith("hi") || l.startsWith("hin") -> "hi"
                l.startsWith("hu") || l.startsWith("hun") -> "hu"
                l.startsWith("id") || l.startsWith("ind") -> "id"
                l.startsWith("it") || l.startsWith("ita") -> "it"
                l.startsWith("ja") || l.startsWith("jpn") -> "ja"
                l.startsWith("ko") || l.startsWith("kor") -> "ko"
                l.startsWith("lv") || l.startsWith("lav") -> "lv"
                l.startsWith("lt") || l.startsWith("lit") -> "lt"
                l.startsWith("pl") || l.startsWith("pol") -> "pl"
                l.startsWith("pt") || l.startsWith("por") -> "pt"
                l.startsWith("ro") || l.startsWith("ron") || l.startsWith("rum") -> "ro"
                l.startsWith("ru") || l.startsWith("rus") -> "ru"
                l.startsWith("sk") || l.startsWith("slk") || l.startsWith("slo") -> "sk"
                l.startsWith("sl") || l.startsWith("slv") -> "sl"
                l.startsWith("es") || l.startsWith("spa") -> "es"
                l.startsWith("sv") || l.startsWith("swe") -> "sv"
                l.startsWith("tr") || l.startsWith("tur") -> "tr"
                l.startsWith("uk") || l.startsWith("ukr") -> "uk"
                l.startsWith("vi") || l.startsWith("vie") -> "vi"
                else -> "en"
            }
        }

        /**
         * Display-name string resources for languages exposed in the language picker UI.
         * Keys are the canonical 2-letter codes; values are R.string resource IDs.
         * Only languages with a corresponding string resource are listed here.
         */
        val LANG_DISPLAY_RES: Map<String, Int> = mapOf(
            "en" to R.string.lang_english,
            "fr" to R.string.lang_french,
            "pt" to R.string.lang_portuguese,
            "es" to R.string.lang_spanish,
            "ko" to R.string.lang_korean,
            "ja" to R.string.lang_japanese,
            "de" to R.string.lang_german,
            "it" to R.string.lang_italian,
            "nl" to R.string.lang_dutch,
            "pl" to R.string.lang_polish,
            "ru" to R.string.lang_russian,
            "tr" to R.string.lang_turkish,
            "ar" to R.string.lang_arabic,
            "hi" to R.string.lang_hindi,
            "vi" to R.string.lang_vietnamese,
            "id" to R.string.lang_indonesian,
            "cs" to R.string.lang_czech,
            "sv" to R.string.lang_swedish,
            "da" to R.string.lang_danish,
            "fi" to R.string.lang_finnish,
            "hu" to R.string.lang_hungarian,
            "ro" to R.string.lang_romanian,
            "el" to R.string.lang_greek,
            "bg" to R.string.lang_bulgarian,
            "uk" to R.string.lang_ukrainian,
            "hr" to R.string.lang_croatian,
            "et" to R.string.lang_estonian,
            "lv" to R.string.lang_latvian,
            "lt" to R.string.lang_lithuanian,
            "sk" to R.string.lang_slovak,
            "sl" to R.string.lang_slovenian,
        )
    }
}
