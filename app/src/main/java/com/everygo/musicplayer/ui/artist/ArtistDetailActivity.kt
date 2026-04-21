package com.everygo.musicplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.everygo.musicplayer.databinding.ActivityArtistDetailBinding

class ArtistDetailActivity : BaseMusicActivity() {

    companion object {
        const val EXTRA_ARTIST_NAME = "extra_artist_name"
    }

    private lateinit var binding: ActivityArtistDetailBinding
    private lateinit var adapter: SongAdapter

    private var artistName: String = ""
    private var songs: List<Song> = emptyList()

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadFromCache()
            applyFilter(SongCache.load(this) ?: emptyList())
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            scanSongs { allSongs -> applyFilter(allSongs) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        artistName = intent.getStringExtra(EXTRA_ARTIST_NAME) ?: ""
        binding.tvArtistName.text = artistName
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayAll.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            PlayerActivity.pendingPlaylist = songs
            musicService?.playlist = songs
            musicService?.playSong(if (musicService?.isShuffleEnabled == true) songs.indices.random() else 0)
        }

        setupRecyclerView()
        loadSongs()
        setupMiniPlayer()
        bindMusicService()
    }

    override fun onServiceReady() {
        musicService?.currentSong?.let {
            showMiniPlayer(it)
            adapter.setCurrentSong(it.id)
        }
        updateRandomIcon()
    }

    override fun onSongChanged(song: Song) {
        showMiniPlayer(song)
        adapter.setCurrentSong(song.id)
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        updatePlayPauseIcon(playing)
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onSongClick = { song, _ ->
                val realIndex = songs.indexOfFirst { it.id == song.id }
                if (realIndex != -1) {
                    PlayerActivity.pendingPlaylist = songs
                    musicService?.playlist = songs
                    musicService?.playSong(realIndex)
                }
            },
            onEditClick = { song ->
                val intent = Intent(this, EditMetadataActivity::class.java).apply {
                    putExtra(EditMetadataActivity.EXTRA_SONG_ID,      song.id.toString())
                    putExtra(EditMetadataActivity.EXTRA_SONG_URI,     song.uri.toString())
                    putExtra(EditMetadataActivity.EXTRA_TITLE,        song.title)
                    putExtra(EditMetadataActivity.EXTRA_ARTIST,       song.artist)
                    putExtra(EditMetadataActivity.EXTRA_ALBUM_ARTIST, song.albumArtist)
                    putExtra(EditMetadataActivity.EXTRA_ALBUM,        song.album)
                    putExtra(EditMetadataActivity.EXTRA_YEAR,         song.year)
                    putExtra(EditMetadataActivity.EXTRA_TRACK_NUMBER, song.trackNumber.toString())
                    putExtra(EditMetadataActivity.EXTRA_GENRE,        song.genre)
                }
                editMetadataLauncher.launch(intent)
            },
            onDeleteClick = { song ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = MediaStore.createDeleteRequest(
                        contentResolver, listOf(song.uri)
                    )
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                } else {
                    contentResolver.delete(song.uri, null, null)
                    scanSongs { allSongs -> applyFilter(allSongs) }
                }
            },
            onPlayNextClick = { song ->
                musicService?.playNextSong(song)
            },
            currentArtistFilter = artistName
        )
        binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSongs.adapter = adapter
    }

    private fun loadSongs() {
        Thread {
            val cached = SongCache.load(this) ?: emptyList()
            runOnUiThread { applyFilter(cached) }
        }.start()
    }

    private fun applyFilter(allSongs: List<Song>) {
        songs = allSongs.filter { song ->
            song.artist
                .split(",")
                .map { it.trim() }
                .any {
                    it.equals(artistName, ignoreCase = true) ||
                            it.fixEncoding().equals(artistName, ignoreCase = true) ||
                            it.equals(artistName.fixEncoding(), ignoreCase = true)
                }
        }.sortedBy { it.title.sortKey() }
        adapter.submitList(songs)
    }
}