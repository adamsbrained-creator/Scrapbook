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

/** A formatting span in a note: {c:ff0000 s:20 u|text}. c is the text color (hex), s the size in sp, u underlines. */
val SPAN_RE = Regex("\\{((?:c:[0-9a-fA-F]{6}|s:\\d{1,3}|u)(?: (?:c:[0-9a-fA-F]{6}|s:\\d{1,3}|u)){0,2})\\|(.+?)\\}")

data class SpanAttrs(val color: String? = null, val size: Int? = null, val underline: Boolean = false) {
    val none get() = color == null && size == null && !underline
    /** The attribute list as written inside the braces, for example "c:ff0000 s:20 u". */
    fun tag() = listOfNotNull(color?.let { "c:$it" }, size?.let { "s:$it" }, if (underline) "u" else null).joinToString(" ")
}

fun parseSpanAttrs(src: String): SpanAttrs {
    var c: String? = null; var s: Int? = null; var u = false
    for (tok in src.split(' ')) when {
        tok.startsWith("c:") -> c = tok.substring(2).lowercase()
        tok.startsWith("s:") -> s = tok.substring(2).toIntOrNull()?.coerceIn(8, 96)
        tok == "u" -> u = true
    }
    return SpanAttrs(c, s, u)
}

/** Layout tags that sit right after a line's prefix: {i:1} to {i:4} indents the line, {a:c} centers it, {a:r} right-aligns it. */
val LINE_TAGS = Regex("^(?:\\{i:([1-4])\\})?(?:\\{a:([cr])\\})?")
val LINE_PREFIX_RE = Regex("^(?:## |# |> |- \\[[ x]\\] |- |\\d+\\. )")

/** Plain-text preview of a formatted scrap: drops list, heading and quote markers, layout tags, inline formatting marks and dividers. */
fun plainPreview(text: String, sep: String = " · "): String =
    text.lines().map { l -> stripInline(l.replace(LINE_PREFIX_RE, "").replace(LINE_TAGS, "")) }
        .filter { it.isNotBlank() && it != "---" }.joinToString(sep)

/** Removes **bold**, *italic*, ~~strike~~, ==highlight== and {color|size|underline} marks and [link](url) syntax, keeping the text. */
fun stripInline(s0: String): String = SPAN_RE.replace(s0) { it.groupValues[2] }.let { s -> s.replace(Regex("\\[([^\\]\\n]+)\\]\\([^)\\s]*\\)"), "\$1").replace(Regex("\\*\\*(.+?)\\*\\*"), "\$1").replace(Regex("~~(.+?)~~"), "\$1")
    .replace(Regex("==(.+?)=="), "\$1").replace(Regex("\\*(.+?)\\*"), "\$1") }

/**
 * A note without a typed title gets its first line as title. This drops that first line from the body
 * when showing the note, so the title is not repeated. Only plain lines and # headings count, never list items.
 */
fun bodyWithoutTitle(title: String, body: String): String {
    val lines = body.lines(); val i = lines.indexOfFirst { it.isNotBlank() }
    if (i < 0 || Regex("^(> |- |\\d+\\. )").containsMatchIn(lines[i]) || plainPreview(lines[i]).trim() != title.trim()) return body
    return lines.drop(i + 1).dropWhile { it.isBlank() }.joinToString("\n")
}
