package com.bookmarktv.app

import java.net.HttpURLConnection
import java.net.URL

/**
 * Best-effort fetch of a page's <title> and a representative thumbnail
 * (og:image, falling back to /favicon.ico) so the home screen card isn't blank.
 * This is intentionally simple regex parsing rather than a full HTML parser --
 * good enough for reading two meta values out of the <head>.
 */
object SiteMetadataFetcher {

    data class SiteMeta(val title: String, val thumbnailUrl: String?)

    fun fetch(rawUrl: String): SiteMeta {
        val url = normalize(rawUrl)
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; Linux; Tizen) BookmarkTV/1.0")
            }
            val html = conn.inputStream.bufferedReader().use { it.readText().take(60_000) }

            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)?.trim()
                ?: URL(url).host

            val ogImage = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""")
                .find(html)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""")
                    .find(html)?.groupValues?.get(1)

            val thumbnail = ogImage ?: "${URL(url).protocol}://${URL(url).host}/favicon.ico"

            SiteMeta(title, thumbnail)
        } catch (e: Exception) {
            SiteMeta(runCatching { URL(url).host }.getOrDefault(rawUrl), null)
        }
    }

    private fun normalize(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }
}
