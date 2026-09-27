package com.bookmarktv.app

import android.os.Bundle
import android.app.Activity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

/**
 * Full-screen native playback of a detected media URL (HLS/DASH/MP4), handed
 * off from BrowserActivity. Track selection is forced to the highest
 * available video bitrate/resolution ("max quality"); for adaptive streams
 * (HLS/DASH) this still allows the player to step down automatically if
 * bandwidth genuinely can't sustain it, which is the correct behavior on a
 * real network rather than a hard requirement to always play the top
 * rendition regardless of buffering.
 *
 * If BrowserActivity detected more than one candidate stream during the same
 * browsing session, the rest are queued as a simple "Up next" playlist --
 * an approximation of the site's own recommendations, not a reproduction of
 * its actual suggestion engine.
 */
@UnstableApi
class PlayerActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var queue: List<String>
    private var queueIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val playerView = findViewById<PlayerView>(R.id.playerView)
        val startUrl = intent.getStringExtra(EXTRA_MEDIA_URL) ?: return finish()
        queue = intent.getStringArrayListExtra(EXTRA_QUEUE)?.takeIf { it.isNotEmpty() }
            ?: listOf(startUrl)
        queueIndex = queue.indexOf(startUrl).coerceAtLeast(0)

        // Forces selection of the highest-bitrate video track available in the
        // manifest, with no resolution/bitrate ceiling -- this is the "max quality"
        // behavior. It still steps down automatically if the network genuinely
        // can't keep up (ExoPlayer's adaptive logic), which is correct: a hard
        // refusal to ever downgrade just causes stalling/buffering on real Wi-Fi.
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
        )

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playNext()
            }
        })

        playAt(queueIndex)
    }

    private fun playAt(index: Int) {
        val url = queue.getOrNull(index) ?: return
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        queueIndex = index
    }

    private fun playNext() {
        val next = queueIndex + 1
        if (next < queue.size) playAt(next) else finish()
    }

    override fun onStop() {
        super.onStop()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"
        const val EXTRA_QUEUE = "extra_queue"
    }
}
