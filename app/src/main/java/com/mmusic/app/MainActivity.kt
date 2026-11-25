package com.mmusic.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var controller: MediaController? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var playing = false
    @Volatile private var pos = 0L
    @Volatile private var dur = 0L
    @Volatile private var ended = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        connectPlayer()

        web = WebView(this)
        setContentView(web)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: ""
                val inside = listOf("blogspot.com", "youtube.com", "google.com", "gstatic.com", "googleapis.com",
                    "ytimg.com", "apple.com", "mzstatic.com", "googleusercontent.com")
                if (inside.any { host.endsWith(it) }) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }
        web.loadUrl(getString(R.string.blog_url))
    }

    private fun connectPlayer() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { ended = state == Player.STATE_ENDED }
            })
            tick()
        }, { r -> main.post(r) })
    }

    private fun tick() {
        controller?.let {
            playing = it.isPlaying
            pos = it.currentPosition.coerceAtLeast(0)
            dur = if (it.duration > 0) it.duration else 0
        }
        main.postDelayed({ tick() }, 300)
    }

    inner class Bridge {
        @JavascriptInterface
        fun play(url: String, title: String, artist: String, art: String) {
            main.post {
                val md = MediaMetadata.Builder().setTitle(title).setArtist(artist)
                if (art.isNotEmpty()) md.setArtworkUri(Uri.parse(art))
                ended = false
                controller?.apply {
                    setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(md.build()).build())
                    prepare()
                    play()
                }
            }
        }
        @JavascriptInterface fun resume() { main.post { controller?.play() } }
        @JavascriptInterface fun pause() { main.post { controller?.pause() } }
        @JavascriptInterface fun seek(ms: Long) { main.post { controller?.seekTo(ms) } }
        @JavascriptInterface
        fun state(): String = "{\"playing\":$playing,\"pos\":$pos,\"dur\":$dur,\"ended\":$ended}"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
