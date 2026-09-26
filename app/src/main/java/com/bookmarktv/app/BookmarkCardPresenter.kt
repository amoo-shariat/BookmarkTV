package com.bookmarktv.app

import android.graphics.Color
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide

private const val CARD_WIDTH = 320
private const val CARD_HEIGHT = 180

/**
 * Renders each Bookmark as a Leanback ImageCardView tile on the home screen.
 * Long-press (remote "select and hold", or long click) removes a bookmark --
 * invokes onLongPress so the caller can update the repository and refresh.
 */
class BookmarkCardPresenter(
    private val onLongPress: (Bookmark) -> Unit = {}
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setInfoAreaBackgroundColor(Color.parseColor("#161B22"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val cardView = viewHolder.view as ImageCardView
        when (item) {
            is Bookmark -> {
                cardView.titleText = item.title
                cardView.contentText = item.url
                cardView.setOnLongClickListener {
                    onLongPress(item)
                    true
                }
                cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
                if (item.thumbnailUrl != null) {
                    Glide.with(cardView.context)
                        .load(item.thumbnailUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(cardView.mainImageView)
                } else {
                    cardView.mainImage = cardView.context.getDrawable(R.drawable.ic_launcher_foreground)
                }
            }
            is AddBookmarkItem -> {
                cardView.titleText = cardView.context.getString(R.string.add_bookmark)
                cardView.contentText = "+"
                cardView.mainImage = cardView.context.getDrawable(R.drawable.ic_launcher_foreground)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).badgeImage = null
        (viewHolder.view as ImageCardView).mainImage = null
    }
}

/** Sentinel row item that renders as the "+" add-a-site tile. */
object AddBookmarkItem
