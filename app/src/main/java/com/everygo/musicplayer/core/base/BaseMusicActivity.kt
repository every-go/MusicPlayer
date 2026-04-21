package com.everygo.musicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

abstract class BaseMusicActivity : AppCompatActivity() {

    var musicService: MusicService? = null
    var isBound = false

    protected val libraryViewModel: LibraryViewModel by viewModels()

    open fun onSongChanged(song: Song) {}
    open fun onPlaybackStateChanged(playing: Boolean) {}
    open fun onServiceReady() {}

    private val songChangedListener: (Song) -> Unit = { song ->
        runOnUiThread { onSongChanged(song) }
    }

    private val playbackStateListener: (Boolean) -> Unit = { playing ->
        runOnUiThread { onPlaybackStateChanged(playing) }
    }

    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true
            onServiceReady()
            musicService!!.onSongChanged.add(songChangedListener)
            musicService!!.onPlaybackStateChanged.add(playbackStateListener)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    fun bindMusicService() {
        Intent(this, MusicService::class.java).also {
            bindService(it, connection, BIND_AUTO_CREATE)
        }
    }

    fun setupMiniPlayer() {
        val miniPlayer = findViewById<View>(R.id.miniPlayer) ?: return

        miniPlayer.setOnClickListener {
            if (musicService?.currentSong != null) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putParcelableArrayListExtra(
                        "playlist",
                        ArrayList(musicService!!.playlist)
                    )
                    putExtra("song_index", musicService!!.currentIndex)
                }
                startActivity(intent)
                startActivity(intent)
            }
        }

        findViewById<ImageButton>(R.id.miniRandom)?.setOnClickListener {
            musicService?.toggleShuffle()
            updateRandomIcon()
        }
        findViewById<ImageButton>(R.id.miniPlayPause)?.setOnClickListener {
            musicService?.togglePlayPause()
        }
        findViewById<ImageButton>(R.id.miniNext)?.setOnClickListener {
            musicService?.playNext()
        }
        findViewById<ImageButton>(R.id.miniPrevious)?.setOnClickListener {
            musicService?.playPrevious()
        }
    }

    open fun showMiniPlayer(song: Song) {
        val miniPlayer = findViewById<View>(R.id.miniPlayer) ?: return
        miniPlayer.visibility = View.VISIBLE
        findViewById<TextView>(R.id.miniTitle)?.text = song.title
        findViewById<TextView>(R.id.miniArtist)?.text = song.artist
        updatePlayPauseIcon(musicService?.isPlaying ?: false)

        Thread {
            val bmp = SongRepository.getAlbumArt(this, song)
            runOnUiThread {
                val iv = findViewById<ImageView>(R.id.miniAlbumArt)
                if (bmp != null) iv?.setImageBitmap(bmp)
                else iv?.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }.start()
    }

    open fun updatePlayPauseIcon(playing: Boolean) {
        findViewById<ImageButton>(R.id.miniPlayPause)?.setImageResource(
            if (playing) R.drawable.ic_pause_white_24dp
            else R.drawable.ic_play_white_24dp
        )
    }

    open fun updateRandomIcon() {
        findViewById<ImageButton>(R.id.miniRandom)?.setImageResource(
            if (musicService?.isShuffleEnabled == true) R.drawable.ic_random_white_24dp
            else R.drawable.ic_random_off_white_24dp
        )
    }

    override fun onDestroy() {
        if (isBound) {
            musicService?.onSongChanged?.remove(songChangedListener)
            musicService?.onPlaybackStateChanged?.remove(playbackStateListener)
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    open fun scanSongs(onComplete: ((List<Song>) -> Unit)? = null) {
        Thread {
            val result = SongRepository.getAllSongs(this)
            SongCache.save(this, result)
            runOnUiThread {
                libraryViewModel.setSongs(result)
                onComplete?.invoke(result)
            }
        }.start()
    }

    fun reloadFromCache() {
        Thread {
            val cached = SongCache.load(this) ?: return@Thread
            runOnUiThread {
                libraryViewModel.setSongs(cached)
            }
        }.start()
    }
}