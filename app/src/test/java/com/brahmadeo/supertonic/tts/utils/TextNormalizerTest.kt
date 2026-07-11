package com.brahmadeo.supertonic.tts.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun testEnglishSentenceSplitting() {
        val normalizer = TextNormalizer()
        val text = "Hello Mr. Smith. How are you today? I am doing fine, thanks!"
        
        // Under 300 characters, these are grouped into a single chunk
        val chunks = normalizer.splitIntoSentences(text, "en")
        
        assertEquals(1, chunks.size)
        assertEquals("Hello Mr. Smith. How are you today? I am doing fine, thanks!", chunks[0])
    }

    @Test
    fun testEnglishChunkingLongText() {
        val normalizer = TextNormalizer()
        // Each sentence is around 100-150 characters.
        val s1 = "Hello Mr. Smith, this is a very long sentence designed to fill up some space in the chunking test so that we can verify that the text is correctly split." // 154 chars
        val s2 = "We want to make sure that the sentence boundaries are respected and that the final chunks are well under the limit of three hundred characters." // 143 chars
        val s3 = "This is a third sentence that should definitely be pushed into a separate chunk because the combined length exceeds the limit." // 126 chars
        
        val text = "$s1 $s2 $s3"
        val chunks = normalizer.splitIntoSentences(text, "en")
        
        // s1 (154) + s2 (143) + 1 = 298 <= 300 -> s1 + s2 forms Chunk 0.
        // s3 (126) forms Chunk 1.
        assertEquals(2, chunks.size)
        assertEquals("$s1 $s2", chunks[0])
        assertEquals(s3, chunks[1])
    }

    @Test
    fun testHindiSentenceSplitting() {
        val normalizer = TextNormalizer()
        val text = "पहला पड़ाव था- UAE। मोदी यहां करीब 3 घंटे रुके। राष्ट्रपति शेख मोहम्मद बिन जायद से मुलाकात की।"
        
        val chunks = normalizer.splitIntoSentences(text, "hi")
        
        // Under 300 characters, these are grouped into a single chunk
        assertEquals(1, chunks.size)
        assertEquals("पहला पड़ाव था- UAE। मोदी यहां करीब 3 घंटे रुके। राष्ट्रपति शेख मोहम्मद बिन जायद से मुलाकात की।", chunks[0])
    }

    @Test
    fun testHindiChunkingLongText() {
        val normalizer = TextNormalizer()
        val s1 = "पहला पड़ाव संयुक्त अरब अमीरात यानी यूएई था जहां प्रधानमंत्री नरेंद्र मोदी करीब तीन घंटे रुके और वहां के राष्ट्रपति शेख मोहम्मद बिन जायद से अत्यंत महत्वपूर्ण मुलाकात की।" // 172 chars
        val s2 = "इस ऐतिहासिक बैठक के दौरान दोनों देशों के बीच कुल सात महत्वपूर्ण सहमति पत्रों पर हस्ताक्षर किए गए जिनमें सबसे प्रमुख रणनीतिक पेट्रोलियम रिजर्व समझौता था।" // 153 chars
        val s3 = "इस नए समझौते के तहत अब दोनों देश मिलकर ऊर्जा सुरक्षा के क्षेत्र में एक दूसरे का सहयोग करेंगे और व्यापारिक रिश्तों को और अधिक मजबूत बनाएंगे।" // 142 chars
        
        val text = "$s1 $s2 $s3"
        val chunks = normalizer.splitIntoSentences(text, "hi")
        
        // s1 (172) + s2 (153) + 1 = 326 > 300 -> s1 forms Chunk 0.
        // s2 (153) + s3 (142) + 1 = 296 <= 300 -> s2 + s3 forms Chunk 1.
        assertEquals(2, chunks.size)
        assertEquals(s1, chunks[0])
        assertEquals("$s2 $s3", chunks[1])
    }

    @Test
    fun testJapaneseSentenceSplitting() {
        val normalizer = TextNormalizer()
        val text = "こんにちは。元気ですか？良い天気ですね。"
        
        val chunks = normalizer.splitIntoSentences(text, "ja")
        
        // Under 120 characters, these are grouped into a single chunk
        assertEquals(1, chunks.size)
        assertEquals("こんにちは。元気ですか？良い天気ですね。", chunks[0])
    }

    @Test
    fun testJapaneseChunkingLongText() {
        val normalizer = TextNormalizer()
        // Japanese maxChunkLen = 120
        val s1 = "本日は晴天なり、非常に気持ちの良い風が吹いており、散歩をするには最適な一日になること間違いなしです。" // 51 chars
        val s2 = "このような素晴らしい日には、外に出て新鮮な空気を吸い、日頃の運動不足を解消するために少し長めに歩くのが良いでしょう。" // 61 chars
        val s3 = "皆さんもぜひ健康維持のために、日々の生活の中に適度なウォーキングや軽い運動を取り入れてみてはいかがでしょうか？" // 57 chars
        
        val text = "$s1$s2$s3"
        val chunks = normalizer.splitIntoSentences(text, "ja")
        
        // s1 (51) + s2 (61) = 112 <= 120 -> s1 + s2 forms Chunk 0.
        // s3 (57) forms Chunk 1.
        assertEquals(2, chunks.size)
        assertEquals("$s1$s2", chunks[0])
        assertEquals(s3, chunks[1])
    }

    @Test
    fun testChunkLengthConstraints() {
        val normalizer = TextNormalizer()
        
        // Japanese/Korean should limit chunks to 120 characters
        val longJa = "この文章は非常に長いです、だからどこかで分割される必要があります、例えば読点やスペースの位置で分割されるのが自然です、そうでないと一文が長すぎて処理に支障をきたす可能性があります。"
        val chunks = normalizer.splitIntoSentences(longJa, "ja")
        
        // It should split at commas to avoid exceeding 120 chars
        for (chunk in chunks) {
            assert(chunk.length <= 120) { "Chunk exceeds 120 chars: '$chunk' (length: ${chunk.length})" }
        }
    }

    @Test
    fun testHindiNumberNormalization() {
        val normalizer = TextNormalizer()
        
        // Integer
        assertEquals("तीन", normalizer.normalize("3", "hi"))
        assertEquals("दो सौ", normalizer.normalize("200", "hi"))
        
        // Decimal
        assertEquals("तिरेपन दशमलव तीन", normalizer.normalize("53.3", "hi"))
        assertEquals("माइनस शून्य दशमलव तीन", normalizer.normalize("-0.3", "hi"))
        
        // Commas inside numbers
        assertEquals("एक लाख पचास हज़ार", normalizer.normalize("1,50,000", "hi"))
        
        // Currency
        assertEquals("पाँच सौ रुपये", normalizer.normalize("₹500", "hi"))
        assertEquals("पाँच सौ रुपये", normalizer.normalize("INR 500", "hi"))
        
        // Percentages
        assertEquals("पाँच प्रतिशत", normalizer.normalize("5%", "hi"))
        
        // Ranges
        assertEquals("दस से पंद्रह", normalizer.normalize("10-15", "hi"))
        
        // Devanagari digits
        assertEquals("तिरेपन दशमलव तीन", normalizer.normalize("५३.३", "hi"))
    }

    @Test
    fun testBulgarianNumberNormalization() {
        val normalizer = TextNormalizer()

        // Integers
        assertEquals("нула", normalizer.normalize("0", "bg"))
        assertEquals("три", normalizer.normalize("3", "bg"))
        assertEquals("двадесет", normalizer.normalize("20", "bg"))
        assertEquals("двадесет и пет", normalizer.normalize("25", "bg"))
        assertEquals("сто", normalizer.normalize("100", "bg"))
        assertEquals("сто и пет", normalizer.normalize("105", "bg"))
        assertEquals("сто двадесет и пет", normalizer.normalize("125", "bg"))
        assertEquals("хиляда и пет", normalizer.normalize("1005", "bg"))
        assertEquals("хиляда сто двадесет и пет", normalizer.normalize("1125", "bg"))
        assertEquals("един милион", normalizer.normalize("1000000", "bg"))
        assertEquals("един милион две хиляди и пет", normalizer.normalize("1002005", "bg"))
        assertEquals("един милион и двадесет и пет хиляди", normalizer.normalize("1025000", "bg"))

        // Decimals (with dot or comma)
        assertEquals("петдесет и три запетая три", normalizer.normalize("53.3", "bg"))
        assertEquals("петдесет и три запетая три", normalizer.normalize("53,3", "bg"))
        assertEquals("минус нула запетая три", normalizer.normalize("-0.3", "bg"))

        // Thousands separator
        assertEquals("петнадесет хиляди", normalizer.normalize("15.000", "bg"))

        // Percentages
        assertEquals("един процент", normalizer.normalize("1%", "bg"))
        assertEquals("два процента", normalizer.normalize("2%", "bg"))
        assertEquals("пет процента", normalizer.normalize("5%", "bg"))
        assertEquals("двадесет и един процент", normalizer.normalize("21%", "bg"))
        assertEquals("двадесет и два процента", normalizer.normalize("22%", "bg"))
        assertEquals("единадесет процента", normalizer.normalize("11%", "bg"))

        // Ranges
        assertEquals("десет до петнадесет", normalizer.normalize("10-15", "bg"))
    }

    @Test
    fun testGermanNumberNormalization() {
        val normalizer = TextNormalizer()

        // Integers
        assertEquals("null", normalizer.normalize("0", "de"))
        assertEquals("drei", normalizer.normalize("3", "de"))
        assertEquals("zwanzig", normalizer.normalize("20", "de"))
        assertEquals("einundzwanzig", normalizer.normalize("21", "de"))
        assertEquals("fünfundzwanzig", normalizer.normalize("25", "de"))
        assertEquals("einhundert", normalizer.normalize("100", "de"))
        assertEquals("einhundertfünf", normalizer.normalize("105", "de"))
        assertEquals("einhundertfünfundzwanzig", normalizer.normalize("125", "de"))
        assertEquals("eintausendfünf", normalizer.normalize("1005", "de"))
        assertEquals("eintausendeinhundertfünfundzwanzig", normalizer.normalize("1125", "de"))
        assertEquals("eine Million", normalizer.normalize("1000000", "de"))
        assertEquals("zwei Millionen fünfhunderttausendeinhundertzwanzig", normalizer.normalize("2500120", "de"))

        // Decimals (with dot or comma)
        assertEquals("dreiundfünfzig Komma drei", normalizer.normalize("53.3", "de"))
        assertEquals("dreiundfünfzig Komma drei", normalizer.normalize("53,3", "de"))
        assertEquals("minus null Komma drei", normalizer.normalize("-0.3", "de"))
        assertEquals("minus null Komma fünf", normalizer.normalize("-0.5", "de"))
        assertEquals("minus null Komma eins", normalizer.normalize("-0.1", "de"))

        // Thousands separator
        assertEquals("fünfzehntausend", normalizer.normalize("15.000", "de"))

        // Percentages
        assertEquals("ein Prozent", normalizer.normalize("1%", "de"))
        assertEquals("fünf Prozent", normalizer.normalize("5%", "de"))
        assertEquals("einhundertein Prozent", normalizer.normalize("101%", "de"))

        // Prefix drops
        assertEquals("einhunderteintausend", normalizer.normalize("101000", "de"))

        // Ranges
        assertEquals("zehn bis fünfzehn", normalizer.normalize("10-15", "de"))
    }

    @Test
    fun testEnglishNumberNormalization() {
        val normalizer = TextNormalizer()
        assertEquals("minus zero point three", normalizer.normalize("-0.3", "en"))
        assertEquals("minus zero point one five", normalizer.normalize("-0.15", "en"))
    }

    @Test
    fun testYearAndCenturyNormalization() {
        val normalizer = TextNormalizer()
        assertEquals("fifteen hundred", normalizer.normalize("1500", "en"))
        assertEquals("fifteen oh six", normalizer.normalize("1506", "en"))
        assertEquals("fifteen fifty six", normalizer.normalize("1556", "en"))
        assertEquals("sixteen hundred", normalizer.normalize("1600", "en"))
        assertEquals("sixteen forty two", normalizer.normalize("1642", "en"))
        assertEquals("seventeen hundred", normalizer.normalize("1700", "en"))
        assertEquals("seventeen ninety eight", normalizer.normalize("1798", "en"))
        assertEquals("eighteen hundred", normalizer.normalize("1800", "en"))
        assertEquals("eighteen oh five", normalizer.normalize("1805", "en"))
        assertEquals("eighteen forty two", normalizer.normalize("1842", "en"))
        assertEquals("nineteen hundred", normalizer.normalize("1900", "en"))
        assertEquals("nineteen oh five", normalizer.normalize("1905", "en"))
        assertEquals("nineteen ninety nine", normalizer.normalize("1999", "en"))
        assertEquals("two thousand", normalizer.normalize("2000", "en"))
        assertEquals("two thousand nine", normalizer.normalize("2009", "en"))
        assertEquals("twenty twenty four", normalizer.normalize("2024", "en"))

        // Centuries
        assertEquals("fifteen hundreds", normalizer.normalize("1500s", "en"))
        assertEquals("eighteen hundreds", normalizer.normalize("1800s", "en"))
        assertEquals("nineteen hundreds", normalizer.normalize("1900s", "en"))
        assertEquals("two thousands", normalizer.normalize("2000s", "en"))
    }

    @Test
    fun testSoftHyphenStripping() {
        val normalizer = TextNormalizer()
        // Unicode soft hyphen is \u00AD. Both words inherently and political contain soft hyphens.
        val text = "in\u00ADher\u00ADent\u00ADly po\u00ADlit\u00ADi\u00ADcal"
        val expected = "inherently political"
        assertEquals(expected, normalizer.normalize(text, "en"))
    }

    @Test
    fun testMcMacFitzNamesNormalization() {
        val normalizer = TextNormalizer()
        
        // Mc names
        assertEquals("Mcdonald", normalizer.normalize("McDonald", "en"))
        assertEquals("Mcgrath", normalizer.normalize("McGrath", "en"))
        assertEquals("Mcclean", normalizer.normalize("McClean", "en"))
        assertEquals("Mckenzie", normalizer.normalize("McKenzie", "en"))
        assertEquals("Mccarthy", normalizer.normalize("McCarthy", "en"))
        assertEquals("Mcguire", normalizer.normalize("McGuire", "en"))
        
        // Mac names
        assertEquals("Macdonald", normalizer.normalize("MacDonald", "en"))
        assertEquals("Macarthur", normalizer.normalize("MacArthur", "en"))
        
        // Fitz names
        assertEquals("Fitzgerald", normalizer.normalize("FitzGerald", "en"))
        
        // A complex sentence containing these names (from user's request)
        val originalText = "Douglas McGrath and Fiona McDonald corporate-sponsored the event alongside Arthur McClean, Brenda McKenzie, Garrett McCarthy, and Seamus McGuire."
        val expectedText = "Douglas Mcgrath and Fiona Mcdonald corporate-sponsored the event alongside Arthur Mcclean, Brenda Mckenzie, Garrett Mccarthy, and Seamus Mcguire."
        assertEquals(expectedText, normalizer.normalize(originalText, "en"))
    }

    @Test
    fun testGodOfWoodsPart1Chunking() {
        val file = java.io.File("src/test/resources/part1_text.txt")
        assert(file.exists()) { "part1_text.txt does not exist at ${file.absolutePath}" }
        val text = file.readText(Charsets.UTF_8)
        val normalizer = TextNormalizer()
        
        val startTime = System.currentTimeMillis()
        val chunks = normalizer.splitIntoSentences(text, "en")
        val endTime = System.currentTimeMillis()
        
        println("Chunked ${text.length} chars into ${chunks.size} chunks in ${endTime - startTime} ms")
        assert(chunks.isNotEmpty())
        for (i in 0 until minOf(10, chunks.size)) {
            println("Chunk $i: ${chunks[i]}")
        }
    }

    @Test
    fun testGodOfWoodsPart1Normalization() {
        val file = java.io.File("src/test/resources/part1_text.txt")
        assert(file.exists())
        val text = file.readText(Charsets.UTF_8)
        val normalizer = TextNormalizer()
        val chunks = normalizer.splitIntoSentences(text, "en")
        
        println("Normalizing ${chunks.size} chunks...")
        val startTime = System.currentTimeMillis()
        for (i in chunks.indices) {
            val chunk = chunks[i]
            try {
                val normalized = normalizer.normalize(chunk, "en", isAdvancedEnabled = true)
                // Just a basic check
                if (i < 5) {
                    println("Chunk $i normalized: $normalized")
                }
            } catch (e: Exception) {
                println("Failed on chunk $i: '$chunk'")
                throw e
            }
        }
        val endTime = System.currentTimeMillis()
        println("Normalized ${chunks.size} chunks in ${endTime - startTime} ms")
    }
}
