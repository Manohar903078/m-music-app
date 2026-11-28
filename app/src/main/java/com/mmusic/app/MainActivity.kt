package com.mmusic.app

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var controller: MediaController? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(MusicBridge(), "Android")
        }
        setContentView(webView)

        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
        }, MoreExecutors.directExecutor())

        // మీ Blogger URL ఇక్కడ పెట్టండి
        webView.loadUrl("https://official.blogspot.com/")

        // Back button నొక్కినప్పుడు యాప్ క్లోజ్ అవ్వకుండా పేజీ/వ్యూ వెనక్కి వెళ్లేలా
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // యాప్ మూసేయకుండా బ్యాక్‌గ్రౌండ్‌లో మ్యూజిక్ కొనసాగేలా
                    moveTaskToBack(true)
                }
            }
        })
    }

    inner class MusicBridge {
        @JavascriptInterface
        fun version(): Int = 1

        @JavascriptInterface
        fun play(url: String, title: String, artist: String, art: String) {
            runOnUiThread {
                val intent = Intent(this@MainActivity, MusicPlaybackService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, intent)

                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .build()

                controller?.apply {
                    setMediaItem(item)
                    prepare()
                    play()
                }
            }
        }

        @JavascriptInterface
        fun pause() {
            runOnUiThread { controller?.pause() }
        }

        @JavascriptInterface
        fun resume() {
            runOnUiThread { controller?.play() }
        }

        @JavascriptInterface
        fun seek(ms: Long) {
            runOnUiThread { controller?.seekTo(ms) }
        }

        @JavascriptInterface
        fun state(): String {
            val c = controller ?: return """{"playing":false,"pos":0,"dur":0,"ended":false}"""
            val playing = c.isPlaying
            val pos = c.currentPosition.coerceAtLeast(0L)
            val dur = if (c.duration > 0) c.duration else 0L
            val ended = c.playbackState == Player.STATE_ENDED
            return JSONObject().apply {
                put("playing", playing)
                put("pos", pos)
                put("dur", dur)
                put("ended", ended)
            }.toString()
        }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}
