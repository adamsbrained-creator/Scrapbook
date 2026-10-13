package app.scrapbook

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTest {
    @Test fun linkTitleUsesHost() = assertEquals("example.com", shareTitle("", "https://www.example.com/a/b", false))
    @Test fun subjectWins() = assertEquals("Hi", shareTitle("Hi", "body", false))
    @Test fun firstLineIsTitle() = assertEquals("First", shareTitle("", "\nFirst\nSecond", false))
    @Test fun urlsExcludeTrailingPunctuation() = assertEquals(listOf(4..16), findUrls("see https://a.b/c, ok"))
    @Test fun titleIsDecoded() = assertEquals("Hello & bye", extractTitle("<html><head><TITLE> Hello &amp;\n bye </TITLE>"))
    @Test fun noTitle() = assertEquals(null, extractTitle("<html></html>"))
    @Test fun previewStripsMarks() = assertEquals("Title · Item", plainPreview("# Title\n- [ ] Item\n---"))
    @Test fun titleLineIsDropped() = assertEquals("World", bodyWithoutTitle("Hello", "Hello\n\nWorld"))
    @Test fun listItemsAreNeverDropped() = assertEquals("- Hello", bodyWithoutTitle("Hello", "- Hello"))
    @Test fun differentTitleKeepsBody() = assertEquals("Hello", bodyWithoutTitle("Hi", "Hello"))
    @Test fun inlineMarksAreStripped() = assertEquals("Bold and it x m", plainPreview("**Bold** and *it* ~~x~~ ==m=="))
    @Test fun numberedItemsLoseTheirNumber() = assertEquals("First", plainPreview("1. First"))
    @Test fun linksKeepOnlyTheirText() = assertEquals("see docs now", plainPreview("see [docs](https://example.com/a) now"))
    @Test fun spansLoseTheirBraces() = assertEquals("red big", plainPreview("{c:ff0000|red} {s:24 u|big}"))
    @Test fun layoutTagsAreStripped() = assertEquals("Hi · Item", plainPreview("{a:c}Hi\n- {i:1}Item"))
    @Test fun spanAttributesRoundTrip() {
        val a = parseSpanAttrs("c:FF0000 s:20 u")
        assertEquals(SpanAttrs("ff0000", 20, true), a)
        assertEquals("c:ff0000 s:20 u", a.tag())
    }
    @Test fun kinds() {
        assertEquals(Kind.Link, classifyShare("https://a.b/c", false))
        assertEquals(Kind.Quote, classifyShare("“Hello”", false))
        assertEquals(Kind.Image, classifyShare("", true))
        assertEquals(Kind.Note, classifyShare("just words", false))
    }
}
