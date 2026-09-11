package br.com.monitordenoticias.desktop

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.Base64

internal object R7HtmlFallback {
    fun extractMediaCandidates(pageUrl: String, proxyUrl: String): List<String> = runCatching {
        val base = URI(pageUrl)
        val connection = open(pageUrl, proxyUrl)
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36")
        connection.setRequestProperty("Referer", pageUrl)
        val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val normalized = html
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("&amp;", "&")

        val absolute = Regex(
            "https?://[^\\s\\\"'<>]+?(?:\\.m3u8|\\.mp4|\\.m4v|\\.webm)(?:\\?[^\\s\\\"'<>]*)?",
            RegexOption.IGNORE_CASE
        ).findAll(normalized).map { it.value }

        val attributed = Regex(
            "(?:src|url|file|contentUrl)\\s*[:=]\\s*[\\\"']([^\\\"']+?(?:\\.m3u8|\\.mp4|\\.m4v|\\.webm)[^\\\"']*)",
            RegexOption.IGNORE_CASE
        ).findAll(normalized)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { base.resolve(it).toString() }

        (absolute + attributed)
            .map { it.trim().trim('"', '\'') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .filterNot { it.contains("doubleclick", true) || it.contains("analytics", true) || it.contains("pixel", true) }
            .distinct()
            .take(15)
            .toList()
    }.getOrDefault(emptyList())

    private fun open(url: String, proxyUrl: String): HttpURLConnection {
        if (proxyUrl.isBlank()) return URL(url).openConnection() as HttpURLConnection
        val uri = URI(proxyUrl.trim())
        val host = uri.host ?: return URL(url).openConnection() as HttpURLConnection
        val port = if (uri.port > 0) uri.port else 8080
        val type = if (uri.scheme.equals("socks5", true) || uri.scheme.equals("socks", true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val connection = URL(url).openConnection(Proxy(type, InetSocketAddress(host, port))) as HttpURLConnection
        if (type == Proxy.Type.HTTP && !uri.userInfo.isNullOrBlank()) {
            val token = Base64.getEncoder().encodeToString(uri.userInfo.toByteArray(Charsets.UTF_8))
            connection.setRequestProperty("Proxy-Authorization", "Basic $token")
        }
        return connection
    }
}
