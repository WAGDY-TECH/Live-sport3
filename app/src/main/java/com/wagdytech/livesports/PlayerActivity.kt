package com.wagdytech.livesports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        hideSystemBars()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val url = intent.getStringExtra(EXTRA_URL)

        playerView = findViewById(R.id.playerView)
        playerView.setControllerShowTimeoutMs(3500)
        setTitle(title)

        findViewById<android.view.View>(R.id.btnQuality).setOnClickListener {
            player?.let { p ->
                TrackSelectionDialogBuilder(this, getString(R.string.quality_title), p, C.TRACK_TYPE_VIDEO)
                    .build()
                    .show()
            }
        }
        findViewById<android.view.View>(R.id.btnResize).setOnClickListener {
            val next = when (playerView.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            playerView.resizeMode = next
            android.widget.Toast.makeText(
                this,
                getString(
                    when (next) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.mode_zoom
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.mode_fill
                        else -> R.string.mode_fit
                    }
                ),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        if (url == null) {
            finish()
            return
        }
        initializePlayer(Uri.parse(url))
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initializePlayer(uri: Uri) {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        R.string.playback_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, title: String, url: String) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_URL, url)
            )
        }
    }
}
