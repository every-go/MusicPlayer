package com.example.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.databinding.ActivityPlaylistDetailBinding

class PlaylistDetailActivity : BaseMusicActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var trackAdapter: PlaylistTrackAdapter

    private var playlistId: String = ""
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: ""
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadPlaylist()

        binding.btnPlayAll.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            PlayerActivity.pendingPlaylist = songs
            musicService?.playlist = songs
            musicService?.playSong(if (musicService?.isShuffleEnabled == true) songs.indices.random() else 0)
        }

        setupMiniPlayer()
        bindMusicService()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylist()
    }

    override fun onServiceReady() {
        musicService?.currentSong?.let { showMiniPlayer(it) }
        updateRandomIcon()
    }

    override fun onSongChanged(song: Song) { showMiniPlayer(song) }
    override fun onPlaybackStateChanged(playing: Boolean) { updatePlayPauseIcon(playing) }

    private fun loadPlaylist() {
        val playlist = PlaylistRepository.load(this).find { it.id == playlistId } ?: return
        binding.tvPlaylistName.text = playlist.name

        val allSongs = SongCache.load(this) ?: emptyList()
        songs = playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
        trackAdapter.submitList(songs)
    }

    private fun setupRecyclerView() {
        trackAdapter = PlaylistTrackAdapter(
            onTrackClick = { song ->
                val index = songs.indexOfFirst { it.id == song.id }
                if (index != -1) {
                    PlayerActivity.pendingPlaylist = songs
                    musicService?.playlist = songs
                    musicService?.playSong(index)
                }
            },
            onRemoveClick = { song ->
                PlaylistRepository.removeSong(this, playlistId, song.id)
                loadPlaylist()
            },
            onDragStart = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )

        binding.recyclerViewPlaylistTracks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPlaylistTracks.adapter = trackAdapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewPlaylistTracks)
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            trackAdapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            // Salva il nuovo ordine
            val newOrder = trackAdapter.currentList.map { it.id }
            PlaylistRepository.reorder(this@PlaylistDetailActivity, playlistId, newOrder)
        }
    })
}