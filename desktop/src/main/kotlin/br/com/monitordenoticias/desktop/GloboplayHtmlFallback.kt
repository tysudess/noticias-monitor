package br.com.monitordenoticias.desktop

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.util.Base64

internal object GloboplayHtmlFallback {
    fun extractM3u8Candidates(pageUrl: String, proxyUrl: String): List<String> = runCatching {
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
        Regex("https?://[^\\s\\\"'<>]+?\\.m3u8[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value }
            .plus(
                Regex("(?:src|url|file)\\s*[:=]\\s*[\\\"']([^\\\"']+?\\.m3u8[^\\\"']*)", RegexOption.IGNORE_CASE)
                    .findAll(normalized)
                    .mapNotNull { it.groupValues.getOrNull(1) }
                    .map { base.resolve(it).toString() }
            )
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .take(15)
            .toList()
    }.getOrDefault(emptyList())

    private fun open(url: String, proxyUrl: String): HttpURLConnection {
        if (proxyUrl.isBlank()) return URL(url).openConnection() as HttpURLConnection
        val uri = URI(proxyUrl.trim())
        val host = uri.host ?: return URL(url).openConnection() as HttpURLConnection
        val port = if (uri.port > 0) uri.port else 8080
        val connection = URL(url).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))) as HttpURLConnection
        if (!uri.userInfo.isNullOrBlank()) {
            val token = Base64.getEncoder().encodeToString(uri.userInfo.toByteArray(Charsets.UTF_8))
            connection.setRequestProperty("Proxy-Authorization", "Basic $token")
        }
        return connection
    }
}
