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
