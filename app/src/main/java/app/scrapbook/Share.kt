package app.scrapbook

private val URL = Regex("^https?://\\S+$")

fun isUrl(text: String) = URL.matches(text.trim())

/** What kind of item a shared payload should become. */
fun classifyShare(text: String, hasImage: Boolean): Kind = when {
    hasImage -> Kind.Image
    isUrl(text) -> Kind.Link
    text.trimStart().firstOrNull() in listOf('"', '“') -> Kind.Quote
    else -> Kind.Note
}

/** A sensible default title for something shared into the app. */
fun shareTitle(subject: String, text: String, hasImage: Boolean): String = when {
    subject.isNotBlank() -> subject.trim()
    isUrl(text) -> runCatching { java.net.URI(text.trim()).host }.getOrNull()?.removePrefix("www.") ?: "Link"
    text.isNotBlank() -> text.lines().first { it.isNotBlank() }.trim().take(60)
    hasImage -> "Shared image"
    else -> "Untitled"
}

/** Character ranges of web links in a text, without trailing punctuation. */
fun findUrls(text: String): List<IntRange> = Regex("https?://\\S+").findAll(text).map { m ->
    var end = m.range.last
    while (end > m.range.first && text[end] in ".,;:!?)\"”'") end--
    m.range.first..end
}.toList()

/** The <title> of an HTML page, decoded and trimmed; null if there is none. */
fun extractTitle(html: String): String? =
    Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.replace("&#39;", "'")?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotEmpty() }?.take(120)
