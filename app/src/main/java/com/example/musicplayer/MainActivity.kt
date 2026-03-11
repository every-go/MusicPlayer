package com.example.musicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    private var musicService: MusicService? = null
    private var isBound = false

    // ---- Listener dichiarati come proprietà per poterli rimuovere ----
    private val songChangedListener: (Song) -> Unit = { song ->
        runOnUiThread { showMiniPlayer(song) }
    }
    private val playbackStateListener: (Boolean) -> Unit = { playing ->
        runOnUiThread { updatePlayPauseIcon(playing) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true

            // Aggiorna la mini bar se c'è già una canzone in riproduzione
            musicService!!.currentSong?.let { showMiniPlayer(it) }

            musicService!!.onSongChanged.add(songChangedListener)
            musicService!!.onPlaybackStateChanged.add(playbackStateListener)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadSongs()
            else Toast.makeText(this, "Permesso necessario per leggere la musica", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupMiniPlayer()
        checkPermissionAndLoad()

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
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

    private fun setupRecyclerView() {
        adapter = SongAdapter { song, index ->
            // Solo questo nel click listener
            PlayerActivity.pendingPlaylist = songs
            musicService?.playlist = songs
            musicService?.playSong(index)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED ->
                loadSongs()
            else ->
                requestPermissionLauncher.launch(permission)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadSongs() {
        Thread {
            val result = SongRepository.getAllSongs(this)
            runOnUiThread {
                songs = result
                adapter.submitList(songs)
                binding.tvSongCount.text = getString(R.string.song_count, songs.size)
                binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            if (musicService?.currentSong != null) {
                // NON settare pendingPlaylist: la canzone è già in corso
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_SONG_INDEX, musicService!!.currentIndex)
                }
                startActivity(intent)
            }
        }

        binding.miniRandom.setOnClickListener   {
            musicService?.toggleShuffle()
            updateRandomIcon()
        }
        binding.miniPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.miniNext.setOnClickListener      { musicService?.playNext() }
        binding.miniPrevious.setOnClickListener  { musicService?.playPrevious() }
    }

    private fun showMiniPlayer(song: Song) {
        binding.miniPlayer.visibility = View.VISIBLE
        binding.miniTitle.text  = song.title
        binding.miniArtist.text = song.artist
        updatePlayPauseIcon(musicService?.isPlaying ?: false)

        Thread {
            val bmp = SongRepository.getAlbumArt(this, song.uri)
            runOnUiThread {
                if (bmp != null) binding.miniAlbumArt.setImageBitmap(bmp)
                else binding.miniAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }.start()
    }

    private fun updatePlayPauseIcon(playing: Boolean) {
        binding.miniPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_white_24dp
            else R.drawable.ic_play_white_24dp
        )
    }

    private fun updateRandomIcon() {
        binding.miniRandom.setImageResource(
            if (musicService?.isShuffleEnabled == true) R.drawable.ic_random_white_24dp
            else R.drawable.ic_random_off_white_24dp
        )
    }
}