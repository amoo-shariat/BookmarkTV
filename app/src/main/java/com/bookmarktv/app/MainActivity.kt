package com.bookmarktv.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV home screen. Shows one row of bookmarked sites (as cards) plus an
 * "Add site" tile. Selecting a bookmark opens BrowserActivity on that URL.
 */
class MainActivity : FragmentActivity() {

    private lateinit var repository: BookmarkRepository
    private lateinit var rowAdapter: ArrayObjectAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = BookmarkRepository(this)

        BackgroundManager.getInstance(this).attach(window)

        val browseFragment = BrowseSupportFragment().apply {
            title = getString(R.string.app_name)
            headersState = BrowseSupportFragment.HEADERS_DISABLED
            isHeadersTransitionOnBackEnabled = false
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, browseFragment)
            .commit()

        val cardPresenter = BookmarkCardPresenter(onLongPress = { bookmark ->
            AlertDialog.Builder(this)
                .setTitle("Remove ${bookmark.title}?")
                .setPositiveButton("Remove") { _, _ ->
                    repository.remove(bookmark.id)
                    refreshRow()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        rowAdapter = ArrayObjectAdapter(cardPresenter)
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        rowsAdapter.add(ListRow(HeaderItem(0, "Your sites"), rowAdapter))
        browseFragment.adapter = rowsAdapter

        browseFragment.onItemViewClickedListener =
            OnItemViewClickedListener { _: Presenter.ViewHolder?, item: Any?, _: RowPresenter.ViewHolder?, _: Row? ->
                when (item) {
                    is Bookmark -> openBrowser(item)
                    is AddBookmarkItem -> showAddBookmarkDialog()
                }
            }

        refreshRow()
    }

    override fun onResume() {
        super.onResume()
        refreshRow()
    }

    private fun refreshRow() {
        rowAdapter.clear()
        rowAdapter.addAll(0, repository.getAll())
        rowAdapter.add(AddBookmarkItem)
    }

    private fun openBrowser(bookmark: Bookmark) {
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_URL, bookmark.url)
            putExtra(BrowserActivity.EXTRA_TITLE, bookmark.title)
        }
        startActivity(intent)
    }

    private fun showAddBookmarkDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.add_bookmark_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_bookmark)
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) fetchAndSave(url)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchAndSave(url: String) {
        Toast.makeText(this, getString(R.string.loading_thumbnail), Toast.LENGTH_SHORT).show()
        scope.launch {
            val meta = withContext(Dispatchers.IO) { SiteMetadataFetcher.fetch(url) }
            repository.add(meta.title, if (url.startsWith("http")) url else "https://$url", meta.thumbnailUrl)
            refreshRow()
        }
    }
}
