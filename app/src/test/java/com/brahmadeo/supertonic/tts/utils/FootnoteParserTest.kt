package com.brahmadeo.supertonic.tts.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class FootnoteParserTest {

    private fun weaveSentences(
        para: String,
        notesMap: Map<Int, String>,
        isOff: Boolean = false
    ): List<String> {
        val mode = if (isOff) EbookParser.FootnoteMode.OFF else EbookParser.FootnoteMode.ALL
        val result = EbookParser.weaveFootnotesIntoParagraphs(para, notesMap, mode)
        return result.split("\n\n").filter { it.isNotBlank() }
    }

    @Test
    fun testMarkerMidSentence() {
        val notes = mapOf(0 to "In 1687, titled Principia.")
        val para = "Newton published his masterwork\u2060FN0\u2060 in London after years of research. These laws established classical mechanics."
        val result = weaveSentences(para, notes)

        assertEquals(3, result.size)
        assertEquals("Newton published his masterwork in London after years of research.", result[0])
        assertEquals("Footnote: In 1687, titled Principia.", result[1])
        assertEquals("These laws established classical mechanics.", result[2])
    }

    @Test
    fun testMarkerAtSentenceEnd() {
        val notes = mapOf(0 to "In 1687, titled Principia.")
        val para = "Newton published his masterwork in London after years of research.\u2060FN0\u2060 Next sentence begins here."
        val result = weaveSentences(para, notes)

        assertEquals(3, result.size)
        assertEquals("Newton published his masterwork in London after years of research.", result[0])
        assertEquals("Footnote: In 1687, titled Principia.", result[1])
        assertEquals("Next sentence begins here.", result[2])
    }

    @Test
    fun testTwoMarkersInOneSentence() {
        val notes = mapOf(
            0 to "In 1687, titled Principia.",
            1 to "Specifically at the Royal Society."
        )
        val para = "Newton published his masterwork\u2060FN0\u2060 in London\u2060FN1\u2060 after years of research. Next sentence."
        val result = weaveSentences(para, notes)

        assertEquals(4, result.size)
        assertEquals("Newton published his masterwork in London after years of research.", result[0])
        assertEquals("Footnote: In 1687, titled Principia.", result[1])
        assertEquals("Footnote: Specifically at the Royal Society.", result[2])
        assertEquals("Next sentence.", result[3])
    }

    @Test
    fun testOffModeStripsMarkerWithoutFootnote() {
        val notes = mapOf(0 to "In 1687, titled Principia.")
        val para = "Newton published his masterwork\u2060FN0\u2060 in London. Next sentence."
        val result = weaveSentences(para, notes, isOff = true)

        assertEquals(2, result.size)
        assertEquals("Newton published his masterwork in London.", result[0])
        assertEquals("Next sentence.", result[1])
    }

    @Test
    fun testCitationOnlyFilterHeuristic() {
        // Pure citations should return true (be skipped)
        assertTrue(EbookParser.isCitationOnly("Ibid., p. 45."))
        assertTrue(EbookParser.isCitationOnly("Op. cit., pp. 12-14."))
        assertTrue(EbookParser.isCitationOnly("https://doi.org/10.1016/j.jmatep.2020.100001"))
        assertTrue(EbookParser.isCitationOnly("Smith, J. (1998), p. 112."))
        assertTrue(EbookParser.isCitationOnly("See also Jones et al. (2015), pp. 100-102."))

        // Genuine prose should return false (be read aloud)
        assertFalse(EbookParser.isCitationOnly("Jews and Christians have interpreted the relevant passages in Genesis in many different ways, but most accept this chronology."))
        assertFalse(EbookParser.isCitationOnly("This formula was originally derived by Isaac Newton in his Cambridge lectures."))
        assertFalse(EbookParser.isCitationOnly("In fact, Machiavelli here refers to the revolt in Florence."))
    }

    @Test
    fun testJsoupNamespacedSelectorAndBacklinkRemoval() {
        val html = """
            <html><body>
                <p>An even graver distortion involves our understanding of infectious diseases. The Bible routinely depicts epidemics as divine punishment<a class="char-enref" href="#en_d1e9202" role="doc-noteref" epub:type="noteref">[17]</a> and claims they can be prevented.</p>
                <div class="footnotes">
                    <div id="en_d1e9202">
                        <p>Epidemics were often described this way in antiquity. <a role="doc-backlink" href="#ref">BACK TO NOTE</a></p>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        // Verify namespaced selector works with escaped colon
        val noterefs = doc.select("a[epub\\:type~=noteref], a[role=doc-noteref]")
        assertEquals(1, noterefs.size)
        assertEquals("[17]", noterefs[0].text())

        // Verify backlink stripping
        val noteDiv = doc.getElementById("en_d1e9202")!!
        val noteCopy = noteDiv.clone()
        noteCopy.select("a[role=doc-backlink]").remove()
        assertEquals("Epidemics were often described this way in antiquity.", noteCopy.text().trim())
    }

    @Test
    fun testResolveRelativePath() {
        assertEquals(
            "OEBPS/xhtml/Hara_9780593734247_epub3_nts_r1.xhtml",
            EbookParser.resolveRelativePath("OEBPS/xhtml/chapter1.xhtml", "Hara_9780593734247_epub3_nts_r1.xhtml")
        )
        assertEquals(
            "OEBPS/Text/glossary.xhtml",
            EbookParser.resolveRelativePath("OEBPS/Text/0105.xhtml", "../Text/glossary.xhtml")
        )
        assertEquals(
            "OEBPS/Text/chapter1.xhtml",
            EbookParser.resolveRelativePath("OEBPS/Text/chapter1.xhtml", "#footnote1")
        )
    }

    @Test
    fun testGetRelativePathAndPathsMatch() {
        assertEquals("xhtml/notes-ch1.xhtml", EbookParser.getRelativePath("xhtml/notes-ch1.xhtml"))
        assertEquals("xhtml/notes-ch1.xhtml", EbookParser.getRelativePath("/xhtml/notes-ch1.xhtml"))
        assertEquals("xhtml/notes-ch1.xhtml", EbookParser.getRelativePath("http://localhost:8080/xhtml/notes-ch1.xhtml"))
        assertEquals("xhtml/notes-ch1.xhtml", EbookParser.getRelativePath("file:///data/app/book.epub/xhtml/notes-ch1.xhtml"))

        assertTrue(EbookParser.pathsMatch("/xhtml/notes-ch1.xhtml", "xhtml/notes-ch1.xhtml"))
        assertTrue(EbookParser.pathsMatch("OEBPS/xhtml/notes-ch1.xhtml", "xhtml/notes-ch1.xhtml"))
        assertFalse(EbookParser.pathsMatch("xhtml/notes-ch1.xhtml", "xhtml/chapter1.xhtml"))
        assertFalse(EbookParser.pathsMatch("xhtml/notes-ch1.xhtml", "xhtml/notes-ch2.xhtml"))
    }

    @Test
    fun testSocratesStyleEndnoteExtraction() {
        val notesHtml = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body epub:type="backmatter">
            <section epub:type="rearnotes" role="doc-endnotes">
            <ol class="footnotes">
            <li epub:type="rearnote" id="notes-ch1-1" value="-0"><p class="NTX"><a hidden="hidden" href="chapter1.xhtml#notes-ch1_1">1.</a> The main source for this account of Socrates’s trial is, of course, Plato’s <i>Apology</i>, although I have also drawn upon material from Xenophon’s <i>Apology</i> and other sources.</p></li>
            </ol>
            </section>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(notesHtml)
        val el = doc.getElementById("notes-ch1-1")!!

        // Call production cleaning method directly
        val noteText = EbookParser.cleanFootnoteElement(el)

        assertEquals(
            "The main source for this account of Socrates’s trial is, of course, Plato’s Apology, although I have also drawn upon material from Xenophon’s Apology and other sources.",
            noteText
        )
        assertFalse(EbookParser.isCitationOnly(noteText!!))
    }
}
