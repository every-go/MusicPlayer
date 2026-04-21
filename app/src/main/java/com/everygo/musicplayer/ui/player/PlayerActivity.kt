package com.everygo.musicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.everygo.musicplayer.databinding.ActivityPlayerBinding
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.provider.MediaStore

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    private var musicService: MusicService? = null
    private var isBound = false

    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekbar = object : Runnable {
        override fun run() {
            val service = musicService ?: return

            binding.seekBar.max = service.duration
            binding.seekBar.progress = service.currentPosition
            binding.tvCurrentTime.text = formatMs(service.currentPosition)
            binding.tvTotalTime.text = formatMs(service.duration)

            handler.postDelayed(this, 500)
        }
    }

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

            val service = musicService ?: return
            val index = intent.getIntExtra(EXTRA_SONG_INDEX, -1)

            if (index >= 0 && musicService!!.currentIndex != index) {
                musicService!!.playSong(index)
            } else {
                musicService!!.currentSong?.let { updateUI(it) }
            }
            val newPlaylist = pendingPlaylist

            if (newPlaylist != null) {
                if (service.currentIndex != index || service.playlist != newPlaylist) {
                    service.playlist = newPlaylist
                    service.playSong(index)
                } else {
                    service.currentSong?.let { updateUI(it) }
                }
                pendingPlaylist = null
            } else {
                service.currentSong?.let { updateUI(it) }
            }

            binding.btnPlayPause.setImageResource(
                if (service.isPlaying) R.drawable.ic_pause_white_24dp
                else R.drawable.ic_play_white_24dp
            )

            updateRandomIcon()

            service.onSongChanged.add(songChangedListener)
            service.onPlaybackStateChanged.add(playbackStateListener)

            handler.post(updateSeekbar)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            musicService = null
        }
    }

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Thread {
                SongCache.load(this)
                runOnUiThread {
                    musicService?.currentSong?.let { updateUI(it) }
                }
            }.start()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    private fun showOverflowMenu(anchor: android.view.View) {
        val song = musicService?.currentSong ?: return

        showSongMenu(
            context = this,
            anchor = anchor,
            song = song,
            onAddToPlaylist = { playlist ->
                PlaylistRepository.addSong(this, playlist.id, song.id)
            },
            onEdit = {
                val intent = Intent(this, EditMetadataActivity::class.java).apply {
                    putExtra(EditMetadataActivity.EXTRA_SONG_ID, song.id.toString())
                    putExtra(EditMetadataActivity.EXTRA_SONG_URI, song.uri.toString())
                    putExtra(EditMetadataActivity.EXTRA_TITLE, song.title)
                    putExtra(EditMetadataActivity.EXTRA_ARTIST, song.artist)
                    putExtra(EditMetadataActivity.EXTRA_ALBUM_ARTIST, song.albumArtist)
                    putExtra(EditMetadataActivity.EXTRA_ALBUM, song.album)
                    putExtra(EditMetadataActivity.EXTRA_YEAR, song.year)
                    putExtra(EditMetadataActivity.EXTRA_TRACK_NUMBER, song.trackNumber.toString())
                    putExtra(EditMetadataActivity.EXTRA_GENRE, song.genre)
                }
                editMetadataLauncher.launch(intent)
            },
            onDelete = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                } else {
                    contentResolver.delete(song.uri, null, null)
                    finish()
                }
            },
            onGoToArtist = { artistName ->
                val intent = Intent(this, ArtistDetailActivity::class.java).apply {
                    putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artistName)
                }
                startActivity(intent)
            },
            onGoToAlbum = {
                val intent = Intent(this, AlbumDetailActivity::class.java).apply {
                    putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, song.album)
                }
                startActivity(intent)
            }
        )
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

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                vX: Float,
                vY: Float
            ): Boolean {

                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
                    kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                    kotlin.math.abs(vX) > SWIPE_VELOCITY
                ) {
                    if (diffX > 0) musicService?.playPrevious()
                    else musicService?.playNext()
                    return true
                }

                if (kotlin.math.abs(diffY) > kotlin.math.abs(diffX) &&
                    diffY > SWIPE_THRESHOLD &&
                    kotlin.math.abs(vY) > SWIPE_VELOCITY
                ) {
                    finish()
                    return true
                }

                return false
            }
        })

        (binding.root).setGestureDetector(gestureDetector)
    }

    private fun setupButtons() {
        binding.btnRandom.setOnClickListener {
            musicService?.toggleShuffle()
            updateRandomIcon()
        }

        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.playPrevious()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnOverflow.setOnClickListener { showOverflowMenu(it) }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: android.widget.SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) musicService?.seekTo(progress)
                }

                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            }
        )
    }

    private fun updateUI(song: Song) {
        runOnUiThread {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvAlbum.text = song.album

            Thread {
                val bmp = SongRepository.getAlbumArt(this, song.uri)
                runOnUiThread {
                    binding.ivAlbumArt.setImageBitmap(
                    bmp ?: androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(this, R.drawable.ic_launcher_foreground)
                        ?.toBitmap()
                )
                }
            }.start()
        }
    }

    private fun updateRandomIcon() {
        binding.btnRandom.setImageResource(
            if (musicService?.isShuffleEnabled == true)
                R.drawable.ic_random_white_24dp
            else
                R.drawable.ic_random_off_white_24dp
        )
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

    private fun formatMs(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    companion object {
        const val EXTRA_SONG_INDEX = "song_index"
        var pendingPlaylist: List<Song>? = null
    }
}