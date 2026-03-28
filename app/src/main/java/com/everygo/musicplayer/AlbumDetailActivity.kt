package com.everygo.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ActivityAlbumDetailBinding
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog

class AlbumDetailActivity : BaseMusicActivity() {

    companion object {
        const val EXTRA_ALBUM_NAME = "extra_album_name"
    }

    private lateinit var binding: ActivityAlbumDetailBinding
    private lateinit var trackAdapter: AlbumTrackAdapter

    private var albumName: String = ""
    private var songs: List<Song> = emptyList()
    private var headerView: View? = null

    private val coverPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveCoverToAlbum(it) }
    }

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadFromCache()
            Thread {
                val cached = SongCache.load(this) ?: emptyList()
                runOnUiThread {
                    songs = cached
                        .filter { it.album.trim().equals(albumName.trim(), ignoreCase = true) }
                        .sortedWith(compareBy({ it.trackNumber }, { it.title.sortKey() }))
                    trackAdapter.submitList(songs)
                }
            }.start()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            scanSongs { allSongs ->
                songs = allSongs
                    .filter { it.album.trim().equals(albumName.trim(), ignoreCase = true) }
                    .sortedWith(compareBy({ it.trackNumber }, { it.title.sortKey() }))
                trackAdapter.submitList(songs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        albumName = intent.getStringExtra(EXTRA_ALBUM_NAME) ?: ""
        binding.tvAlbumTitle.text = albumName
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayAll.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            PlayerActivity.pendingPlaylist = songs
            musicService?.playlist = songs
            musicService?.playSong(if (musicService?.isShuffleEnabled == true) (songs.indices).random() else 0)
        }

        setupRecyclerView()
        loadAlbum()
        setupMiniPlayer()
        bindMusicService()
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

    override fun onPlaybackStateChanged(playing: Boolean) {
        updatePlayPauseIcon(playing)
    }

    private fun loadAlbum() {
        val cached = SongCache.load(this) ?: emptyList()
        songs = cached
            .filter { it.album.trim().equals(albumName.trim(), ignoreCase = true) }
            .sortedWith(compareBy({ it.trackNumber }, { it.title.sortKey() }))

        trackAdapter.submitList(songs)

        val albumArtist = songs.firstOrNull()?.albumArtist?.trim() ?: ""
        updateHeader(albumArtist)
    }

    private fun setupRecyclerView() {
        android.util.Log.d("MusicPlayer", "setupRecyclerView start")
        trackAdapter = AlbumTrackAdapter(
            onTrackClick = { song ->
                val realIndex = songs.indexOfFirst { it.id == song.id }
                if (realIndex != -1) {
                    PlayerActivity.pendingPlaylist = songs
                    musicService?.playlist = songs
                    musicService?.playSong(realIndex)
                }
            },
            onOverflowClick = { song, anchor -> showTrackMenu(song, anchor) }
        )
        android.util.Log.d("MusicPlayer", "trackAdapter created")

        val header = layoutInflater.inflate(R.layout.item_album_header, null, false)
        android.util.Log.d("MusicPlayer", "header inflated")
        headerView = header

        header.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        android.util.Log.d("MusicPlayer", "layoutParams set")

        header.findViewById<android.widget.ImageButton>(R.id.btnEditCover)
            .setOnClickListener { coverPickerLauncher.launch("image/*") }

        val mergedAdapter = androidx.recyclerview.widget.ConcatAdapter(
            HeaderAdapter(header),
            trackAdapter
        )

        binding.recyclerViewTracks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTracks.adapter = mergedAdapter
    }

    private fun updateHeader(albumArtist: String) {
        val header = headerView ?: return
        header.findViewById<android.widget.TextView>(R.id.tvAlbumArtistDetail).text = albumArtist

        val ivCover = header.findViewById<android.widget.ImageView>(R.id.ivAlbumCoverLarge)
        val firstSong = songs.firstOrNull() ?: return

        Thread {
            val bmp = SongRepository.getAlbumArt(this, firstSong)
            runOnUiThread {
                if (bmp != null) ivCover.setImageBitmap(bmp)
                else ivCover.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }.start()
    }

    private fun showTrackMenu(song: Song, anchor: View) {
        // Calcola gli artisti della canzone
        val artistNames = song.artist.split(",").map { it.trim() }

        val popup = PopupMenu(this, anchor)
        val menu = popup.menu

        // Voci standard
        menu.add(0, 0, 0, "Riproduci dopo")
        menu.add(0, 1, 1, "Aggiungi a playlist")
        menu.add(0, 2, 2, "Modifica")
        menu.add(0, 3, 3, "Elimina")

        // Gestione "Vai all'artista" con sottomenu se ci sono più artisti
        if (artistNames.isNotEmpty()) {
            val artistSubMenu = menu.addSubMenu(0, 4, 4, "Vai all'artista")
            artistNames.forEachIndexed { index, artist ->
                artistSubMenu.add(0, 100 + index, index, artist)
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> { // Riproduci dopo
                    musicService?.playNextSong(song)
                    true
                }
                1 -> { // Aggiungi a playlist
                    val playlists = PlaylistRepository.load(this)
                    val playlistNames = playlists.map { it.name }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Aggiungi a playlist")
                        .setItems(playlistNames) { _, which ->
                            PlaylistRepository.addSong(this, playlists[which].id, song.id)
                        }
                        .setNegativeButton("Annulla", null)
                        .show()
                    true
                }
                2 -> { // Modifica
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
                3 -> { // Elimina
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                        deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else {
                        contentResolver.delete(song.uri, null, null)
                        scanSongs { allSongs ->
                            songs = allSongs
                                .filter { it.album.trim().equals(albumName.trim(), ignoreCase = true) }
                                .sortedWith(compareBy({ it.trackNumber }, { it.title.sortKey() }))
                            trackAdapter.submitList(songs)
                        }
                    }
                    true
                }
                in 100..199 -> { // Vai all'artista selezionato
                    val artist = artistNames[item.itemId - 100]
                    val intent = Intent(this, ArtistDetailActivity::class.java).apply {
                        putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artist)
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun saveCoverToAlbum(uri: Uri) {
        Thread {
            try {
                val imageBytes = contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: return@Thread

                songs.forEach { song ->
                    try {
                        val path = getRealPathFromUri(song.uri) ?: return@forEach
                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(java.io.File(path))
                        val tag = audioFile.tagOrCreateAndSetDefault
                        val artwork = org.jaudiotagger.tag.images.ArtworkFactory
                            .createArtworkFromFile(createTempImageFile(imageBytes))
                        tag.deleteField(org.jaudiotagger.tag.FieldKey.COVER_ART)
                        tag.setField(artwork)
                        audioFile.commit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val bmp = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                runOnUiThread {
                    headerView?.findViewById<android.widget.ImageView>(R.id.ivAlbumCoverLarge)
                        ?.setImageBitmap(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun createTempImageFile(bytes: ByteArray): java.io.File {
        val temp = java.io.File.createTempFile("cover", ".jpg", cacheDir)
        temp.writeBytes(bytes)
        return temp
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return contentResolver.query(uri, projection, null, null, null)?.use {
            val col = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            if (it.moveToFirst()) it.getString(col) else null
        }
    }
}