package com.example.musicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayer.databinding.ActivityPlayerBinding
import android.view.GestureDetector
import android.view.MotionEvent

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private var musicService: MusicService? = null
    private var isBound = false

    // Handler per aggiornare la seekbar ogni secondo
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekbar = object : Runnable {
        override fun run() {
            musicService?.let { service ->
                binding.seekBar.max = service.duration
                binding.seekBar.progress = service.currentPosition
                binding.tvCurrentTime.text = formatMs(service.currentPosition)
                binding.tvTotalTime.text = formatMs(service.duration)
            }
            handler.postDelayed(this, 500)
        }
    }

    // ---- Listener dichiarati come proprietà per poterli rimuovere ----
    private val songChangedListener: (Song) -> Unit = { song -> updateUI(song) }
    private val playbackStateListener: (Boolean) -> Unit = { playing ->
        runOnUiThread {
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause_white_24dp
                else R.drawable.ic_play_white_24dp
            )
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true

            val index = intent.getIntExtra(EXTRA_SONG_INDEX, 0)
            val newPlaylist = pendingPlaylist

            if (newPlaylist != null) {
                // Viene dalla RecyclerView
                if (musicService!!.currentIndex != index || musicService!!.playlist != newPlaylist) {
                    musicService!!.playlist = newPlaylist
                    musicService!!.playSong(index)
                } else {
                    updateUI(musicService!!.currentSong!!)
                }
                pendingPlaylist = null
            } else {
                // Viene dalla miniBar: aggiorna solo la UI
                musicService!!.currentSong?.let { updateUI(it) }
            }

            // Questo va sempre eseguito, in entrambi i casi
            binding.btnPlayPause.setImageResource(
                if (musicService!!.isPlaying) R.drawable.ic_pause_white_24dp
                else R.drawable.ic_play_white_24dp
            )
            updateRandomIcon()

            musicService!!.onSongChanged.add(songChangedListener)
            musicService!!.onPlaybackStateChanged.add(playbackStateListener)
            handler.post(updateSeekbar)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSeekBar()

        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
                    kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                    kotlin.math.abs(vX) > SWIPE_VELOCITY) {

                    if (diffX > 0) {
                        debounceClick { musicService?.playPrevious() }
                    } else {
                        debounceClick { musicService?.playNext() }
                    }
                    return true
                }

                // Swipe verticale dall'alto al basso → indietro
                if (kotlin.math.abs(diffY) > kotlin.math.abs(diffX) &&
                    diffY > SWIPE_THRESHOLD &&
                    kotlin.math.abs(vY) > SWIPE_VELOCITY) {
                    finish()
                    return true
                }

                return false
            }
        })

        // Applica alla view root del layout
        (binding.root ).setGestureDetector(gestureDetector)
    }

    private fun setupButtons() {
        binding.btnRandom.setOnClickListener    {
            musicService?.toggleShuffle()
            updateRandomIcon()
        }
        binding.btnPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnNext.setOnClickListener     { debounceClick { musicService?.playNext() } }
        binding.btnPrevious.setOnClickListener { debounceClick { musicService?.playPrevious() } }
        binding.btnBack.setOnClickListener      { finish() }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
        })
    }

    private var lastClickTime = 0L
    private val clickDebounce = 500L

    private fun debounceClick(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime >= clickDebounce) {
            lastClickTime = now
            action()
        }
    }

    private fun updateUI(song: Song) {
        runOnUiThread {
            binding.tvTitle.text  = song.title
            binding.tvArtist.text = song.artist
            binding.tvAlbum.text  = song.album
            Thread {
                val bmp = SongRepository.getAlbumArt(this, song.uri)
                runOnUiThread {
                    if (bmp != null) binding.ivAlbumArt.setImageBitmap(bmp)
                    else binding.ivAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
                }
            }.start()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateSeekbar)
        if (isBound) {
            musicService?.onSongChanged?.remove(songChangedListener)
            musicService?.onPlaybackStateChanged?.remove(playbackStateListener)
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun updateRandomIcon() {
        binding.btnRandom.setImageResource(
            if (musicService?.isShuffleEnabled == true) R.drawable.ic_random_white_24dp
            else R.drawable.ic_random_off_white_24dp
        )
    }

    private fun formatMs(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    companion object {
        const val EXTRA_SONG_INDEX = "song_index"
        var pendingPlaylist: List<Song>? = null
    }
}