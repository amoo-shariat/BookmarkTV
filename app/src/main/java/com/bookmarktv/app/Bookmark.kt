package com.bookmarktv.app

import org.json.JSONObject

/**
 * A single saved site. thumbnailUrl is best-effort (usually the site's og:image
 * or favicon) fetched when the bookmark is added.
 */
data class Bookmark(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): Bookmark = Bookmark(
            id = o.getString("id"),
            title = o.getString("title"),
            url = o.getString("url"),
            thumbnailUrl = if (o.isNull("thumbnailUrl")) null else o.getString("thumbnailUrl")
        )
    }
}
