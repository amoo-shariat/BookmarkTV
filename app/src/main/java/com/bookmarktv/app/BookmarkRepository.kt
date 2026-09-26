package com.bookmarktv.app

import android.content.Context
import org.json.JSONArray
import java.util.UUID

/**
 * Very small persistence layer. Bookmarks are stored as a JSON array in
 * SharedPreferences -- no need for a database for a handful of saved sites.
 */
class BookmarkRepository(context: Context) {

    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    fun getAll(): List<Bookmark> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Bookmark.fromJson(arr.getJSONObject(it)) }
    }

    fun add(title: String, url: String, thumbnailUrl: String?): Bookmark {
        val bookmark = Bookmark(UUID.randomUUID().toString(), title, url, thumbnailUrl)
        val updated = getAll() + bookmark
        save(updated)
        return bookmark
    }

    fun remove(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    private fun save(list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "bookmark_list"
    }
}
