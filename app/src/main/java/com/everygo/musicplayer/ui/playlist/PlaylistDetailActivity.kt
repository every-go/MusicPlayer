package com.everygo.musicplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ActivityPlaylistDetailBinding

class PlaylistDetailActivity : BaseMusicActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var trackAdapter: PlaylistTrackAdapter

    private var playlistId: String = ""
    private var songs: List<Song> = emptyList()

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadFromCache()
            loadPlaylist()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            scanSongs { _ -> loadPlaylist() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // La barra di stato è gestita da android:fitsSystemWindows="true" nel layout
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
        musicService?.currentSong?.let {
            showMiniPlayer(it)
            trackAdapter.setCurrentSong(it.id)
        }
        updateRandomIcon()
    }

    override fun onSongChanged(song: Song) {
        showMiniPlayer(song)
        trackAdapter.setCurrentSong(song.id)
    }

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
            onMenuClick = { song, anchor -> showTrackMenu(song, anchor) },
            onDragStart = { viewHolder -> itemTouchHelper.startDrag(viewHolder) }
        )

        binding.recyclerViewPlaylistTracks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPlaylistTracks.adapter = trackAdapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewPlaylistTracks)
    }

    private fun showTrackMenu(song: Song, anchor: View) {
        val artistNames = song.artist.split(",").map { it.trim() }

        val popup = PopupMenu(this, anchor)
        val menu = popup.menu

        menu.add(0, 0, 0, "Riproduci dopo")
        menu.add(0, 1, 1, "Rimuovi dalla playlist")
        menu.add(0, 2, 2, "Modifica")
        menu.add(0, 3, 3, "Elimina")

        if (artistNames.isNotEmpty()) {
            val artistSubMenu = menu.addSubMenu(0, 4, 4, "Vai all'artista")
            artistNames.forEachIndexed { index, artist ->
                artistSubMenu.add(0, 100 + index, index, artist)
            }
        }

        menu.add(0, 5, 5, "Vai all'album")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> { musicService?.playNextSong(song); true }
                1 -> {
                    PlaylistRepository.removeSong(this, playlistId, song.id)
                    loadPlaylist()
                    true
                }
                2 -> {
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
                    true
                }
                3 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                        deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else {
                        contentResolver.delete(song.uri, null, null)
                        scanSongs { _ -> loadPlaylist() }
                    }
                    true
                }
                in 100..199 -> {
                    val artist = artistNames[item.itemId - 100]
                    startActivity(Intent(this, ArtistDetailActivity::class.java).apply {
                        putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artist)
                    })
                    true
                }
                5 -> {
                    startActivity(Intent(this, AlbumDetailActivity::class.java).apply {
                        putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, song.album)
                    })
                    true
                }
                else -> false
            }
        }
        popup.show()
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
            val newOrder = trackAdapter.currentList.map { it.id }
            PlaylistRepository.reorder(this@PlaylistDetailActivity, playlistId, newOrder)
        }
    })
}
