package com.bookmarktv.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Collections

/**
 * Loads a bookmarked site in a WebView, made D-pad navigable via injected JS
 * (see assets/tvnav.js), while passively watching every network request the
 * page makes for a playable video stream (HLS/DASH manifest or direct MP4).
 * Once one is found, pressing DPAD_CENTER on a focused video/play control
 * (or the "Play detected video" prompt) hands off to PlayerActivity for a
 * proper native, remote-controllable playback experience.
 *
 * This intentionally does not modify or replace the page's own thumbnails,
 * suggestions or playlist UI -- it shows the site's real page, just adapted
 * for remote-control navigation.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusBar: TextView

    // Preserves first-seen order; first HLS/DASH manifest found wins.
    private val detectedManifests = Collections.synchronizedSet(LinkedHashSet<String>())
    private val detectedMp4s = Collections.synchronizedSet(LinkedHashSet<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        statusBar = findViewById(R.id.statusBar)

        val url = intent.getStringExtra(EXTRA_URL) ?: "https://example.com"
        title = intent.getStringExtra(EXTRA_TITLE)

        setupWebView(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            @Suppress("DEPRECATION")
            allowFileAccess = false
            userAgentString = userAgentString?.replace("; wv", "") // some sites gate on "WebView" UA
        }
        webView.addJavascriptInterface(TvBridge(), "TvBridge")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                inspect(request.url.toString())
                // Return null == let the request proceed completely unmodified;
                // we are only observing, never blocking or altering the page.
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectNavScript()
            }
        }

        webView.loadUrl(url)
    }

    private fun injectNavScript() {
        val js = assets.open("tvnav.js").bufferedReader().use { it.readText() }
        webView.evaluateJavascript(js, null)
    }

    private fun inspect(rawUrl: String) {
        val lower = rawUrl.lowercase()
        val path = runCatching { android.net.Uri.parse(rawUrl).path ?: "" }.getOrDefault("")
        val newManifest = (path.endsWith(".m3u8") || path.endsWith(".mpd")) &&
            detectedManifests.add(rawUrl) && detectedManifests.size <= 5
        val newMp4 = path.endsWith(".mp4") && lower.contains("mp4") && detectedMp4s.add(rawUrl)

        if (newManifest || (newMp4 && detectedManifests.isEmpty())) {
            runOnUiThread { updateStatus() }
        }
    }

    private fun updateStatus() {
        val best = bestCandidate()
        statusBar.text = if (best != null) {
            getString(R.string.play_detected) + "  (press SELECT to open player)"
        } else {
            getString(R.string.no_video_detected)
        }
    }

    private fun bestCandidate(): String? =
        detectedManifests.firstOrNull() ?: detectedMp4s.firstOrNull()

    /** Bridge for status updates coming from the injected tvnav.js. */
    inner class TvBridge {
        @JavascriptInterface
        fun onFocusChanged(tag: String, hrefOrSrc: String) {
            // Currently informational only; hook point for future per-element hints.
        }

        @JavascriptInterface
        fun onVideoElementFound(currentSrc: String) {
            if (currentSrc.isNotBlank() && !currentSrc.startsWith("blob:")) {
                inspect(currentSrc)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { navigate("up"); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { navigate("down"); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { navigate("left"); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { navigate("right"); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                handleSelect()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigate(direction: String) {
        webView.evaluateJavascript("window.__tvNavigate && window.__tvNavigate('$direction')", null)
    }

    private fun handleSelect() {
        // If a video stream has already been detected, SELECT jumps straight
        // to the native player instead of clicking whatever the page focused --
        // this is the fast path once you've found a playable video.
        val candidate = bestCandidate()
        if (candidate != null) {
            openPlayer(candidate)
            return
        }
        webView.evaluateJavascript("window.__tvActivate && window.__tvActivate()") { result ->
            if (result == "\"input\"") {
                // Let the WebView show its normal on-screen keyboard for text fields.
                webView.requestFocus()
            }
        }
    }

    private fun openPlayer(mediaUrl: String) {
        val queue = ArrayList<String>().apply {
            addAll(detectedManifests)
            addAll(detectedMp4s.filterNot { detectedManifests.contains(it) })
        }
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl)
            putStringArrayListExtra(PlayerActivity.EXTRA_QUEUE, queue)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }
}
