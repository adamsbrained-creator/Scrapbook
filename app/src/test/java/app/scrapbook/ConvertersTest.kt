package app.scrapbook

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun tagsRoundTrip() = assertEquals(listOf("a", "b c"), c.toTags(c.fromTags(listOf("a", "b c"))))
    @Test fun emptyTags() = assertEquals(emptyList<String>(), c.toTags(c.fromTags(emptyList())))
    @Test fun enumsRoundTrip() {
        assertEquals(Layout.Journal, c.toLayout(c.fromLayout(Layout.Journal)))
        assertEquals(Kind.Quote, c.toKind(c.fromKind(Kind.Quote)))
    }
}
