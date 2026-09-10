from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt"
text = PATH.read_text(encoding="utf-8")

replacement = r'''    private fun parseYoutubeVideosTab(source: VideoSource, html: String, capturedAt: Long): List<VideoItem> {
        if (html.isBlank()) return emptyList()
        val out = linkedMapOf<String, VideoItem>()
        val videoNeedle = "\"videoId\":\""
        var cursor = 0

        while (cursor < html.length && out.size < MAX_YOUTUBE_ITEMS_PER_SCAN) {
            val marker = html.indexOf(videoNeedle, cursor)
            if (marker < 0) break
            val idStart = marker + videoNeedle.length
            val idEnd = html.indexOf('"', idStart)
            if (idEnd < 0) break
            val videoId = html.substring(idStart, idEnd)
            cursor = idEnd + 1
            if (videoId.length < 6) continue

            val blockEnd = (idEnd + 5200).coerceAtMost(html.length)
            val block = html.substring(idEnd, blockEnd)
            val title = extractYoutubeJsonText(block, "\"title\"")
            if (!usefulTitle(title)) continue

            val publishedText = extractYoutubeJsonText(block, "\"publishedTimeText\"")
            val link = canonicalizeUrl("https://www.youtube.com/watch?v=$videoId")
            out.putIfAbsent(
                canonicalKey(link),
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = parseYoutubeRelativeTime(publishedText, capturedAt),
                    link = link,
                    summary = "Canal oficial • ${source.group} • Aba Vídeos",
                    capturedAt = capturedAt
                )
            )
        }
        return out.values.take(MAX_YOUTUBE_ITEMS_PER_SCAN)
    }

    private fun extractYoutubeJsonText(block: String, field: String): String {
        val fieldPos = block.indexOf(field)
        if (fieldPos < 0) return ""
        val end = (fieldPos + 1800).coerceAtMost(block.length)
        val section = block.substring(fieldPos, end)
        val textNeedle = "\"text\":\""
        val simpleNeedle = "\"simpleText\":\""
        val simplePos = section.indexOf(simpleNeedle)
        val textPos = section.indexOf(textNeedle)
        val markerPos: Int
        val markerLength: Int
        if (simplePos >= 0 && (textPos < 0 || simplePos < textPos)) {
            markerPos = simplePos
            markerLength = simpleNeedle.length
        } else if (textPos >= 0) {
            markerPos = textPos
            markerLength = textNeedle.length
        } else {
            return ""
        }

        val start = markerPos + markerLength
        val value = StringBuilder()
        var escaped = false
        var i = start
        while (i < section.length) {
            val ch = section[i]
            if (escaped) {
                when (ch) {
                    'n', 'r', 't' -> value.append(' ')
                    '"' -> value.append('"')
                    '\\' -> value.append('\\')
                    '/' -> value.append('/')
                    else -> value.append(ch)
                }
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                break
            } else {
                value.append(ch)
            }
            i++
        }
        return cleanJsonText(value.toString())
    }

    private fun parseYoutubeRelativeTime(raw: String, capturedAt: Long): Long {
        val text = normalize(raw)
        val n = text.split(' ').firstNotNullOfOrNull { it.toLongOrNull() } ?: return capturedAt
        val millis = when {
            "minuto" in text || "minute" in text -> n * 60_000L
            "hora" in text || "hour" in text -> n * 60L * 60_000L
            "dia" in text || "day" in text -> n * 24L * 60L * 60_000L
            "semana" in text || "week" in text -> n * 7L * 24L * 60L * 60_000L
            "mes" in text || "month" in text -> n * 30L * 24L * 60L * 60_000L
            "ano" in text || "year" in text -> n * 365L * 24L * 60L * 60_000L
            else -> 0L
        }
        return (capturedAt - millis).coerceAtLeast(1L)
    }

'''

pattern = re.compile(
    r"    private fun parseYoutubeVideosTab\(source: VideoSource, html: String, capturedAt: Long\): List<VideoItem> \{.*?\n    private fun isSpecificVideoUrl",
    re.S,
)
if not pattern.search(text):
    raise SystemExit("YouTube parser region not found")
text = pattern.sub(lambda _: replacement + "    private fun isSpecificVideoUrl", text, count=1)
PATH.write_text(text, encoding="utf-8", newline="\n")
print("Fixed YouTube Videos-tab parser")
