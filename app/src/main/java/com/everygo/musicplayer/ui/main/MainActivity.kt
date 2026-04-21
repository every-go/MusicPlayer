package com.everygo.musicplayer

import android.Manifest
import android.provider.MediaStore
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.everygo.musicplayer.databinding.ActivityMainBinding

class MainActivity : BaseMusicActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var searchAdapter: SearchAdapter
    private var isScanning = false

    private val songsFragment get() =
        supportFragmentManager.findFragmentByTag("f0") as? SongsFragment

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadSongs()
            else Toast.makeText(this, "Permesso necessario per leggere la musica", Toast.LENGTH_LONG).show()
        }

    // Callback per chiudere la ricerca con il tasto indietro.
    // È abilitato solo quando la ricerca è aperta, così non interferisce
    // con la normale navigazione quando la ricerca è chiusa.
    private val searchBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSearch()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // Registra il callback per il tasto indietro (inizialmente disabilitato)
        onBackPressedDispatcher.addCallback(this, searchBackCallback)

        setupTabs()
        setupSearchRecyclerView()
        setupHeader()
        checkPermissionAndLoad()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                binding.header.paddingLeft,
                bars.top,
                binding.header.paddingRight,
                binding.header.paddingBottom
            )
            binding.miniPlayer.setPadding(
                binding.miniPlayer.paddingLeft,
                binding.miniPlayer.paddingTop,
                binding.miniPlayer.paddingRight,
                bars.bottom
            )
            insets
        }

        bindMusicService()
    }

    override fun onResume() {
        super.onResume()
        updateRandomIcon()
    }

    override fun onSongChanged(song: Song) {
        showMiniPlayer(song)
        songsFragment?.setCurrentSong(song.id)
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        updatePlayPauseIcon(playing)
    }

    override fun onServiceReady() {
        musicService?.currentSong?.let { showMiniPlayer(it) }
        updateRandomIcon()
    }

    // ---- Setup ----

    private fun setupTabs() {
        binding.viewPager.adapter = LibraryPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "BRANI"
                1 -> "ARTISTI"
                2 -> "ALBUM"
                3 -> "PLAYLIST"
                else -> ""
            }
        }.attach()
    }

    private val searchEditMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) reloadFromCache()
    }

    private val searchDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) scanSongs()
    }

    private fun setupSearchRecyclerView() {
        searchAdapter = SearchAdapter(
            onSongClick = { song ->
                val songs = libraryViewModel.songs.value ?: emptyList()
                val index = songs.indexOfFirst { it.id == song.id }
                if (index != -1) {
                    PlayerActivity.pendingPlaylist = songs
                    musicService?.playlist = songs
                    musicService?.playSong(index)
                }
            },
            onArtistClick = { artistName ->
                val intent = Intent(this, ArtistDetailActivity::class.java).apply {
                    putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artistName)
                }
                startActivity(intent)
            },
            onAlbumClick = { album ->
                val intent = Intent(this, AlbumDetailActivity::class.java).apply {
                    putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, album.title)
                }
                startActivity(intent)
            },
            onSongMenuClick = { song, anchor ->
                showSongMenu(
                    context = this,
                    anchor = anchor,
                    song = song,
                    onPlayNext = { musicService?.playNextSong(song) },
                    onAddToPlaylist = { playlist ->
                        PlaylistRepository.addSong(this, playlist.id, song.id)
                    },
                    onEdit = {
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
                        searchEditMetadataLauncher.launch(intent)
                    },
                    onDelete = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
                            searchDeleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            contentResolver.delete(song.uri, null, null)
                            scanSongs()
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
        )
        binding.recyclerViewSearch.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSearch.adapter = searchAdapter
    }

    private fun setupHeader() {
        binding.btnSearch.setOnClickListener {
            if (binding.tilSearch.isGone) {
                binding.tilSearch.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                // Abilita il callback: da questo momento il tasto indietro chiude la ricerca
                searchBackCallback.isEnabled = true
            } else {
                closeSearch()
            }
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (query.isBlank()) {
                    binding.recyclerViewSearch.visibility = View.GONE
                    binding.viewPager.visibility = View.VISIBLE
                    binding.tabLayout.visibility = View.VISIBLE
                } else {
                    showSearchResults(query)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnPlayAll.setOnClickListener {
            val songs = libraryViewModel.songs.value ?: return@setOnClickListener
            if (songs.isEmpty()) return@setOnClickListener
            musicService?.let {
                it.playlist = songs
                it.playSong((0 until songs.size).random())
                PlayerActivity.pendingPlaylist = songs
            }
        }

        binding.btnOverflow.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_scan -> { scanSongs(); true }
                    else -> false
                }
            }
            popup.show()
        }

        binding.miniPlayer.setOnClickListener {
            if (musicService?.currentSong != null) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_SONG_INDEX, musicService!!.currentIndex)
                }
                startActivity(intent)
            }
        }

        binding.miniRandom.setOnClickListener    { musicService?.toggleShuffle(); updateRandomIcon() }
        binding.miniPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.miniNext.setOnClickListener      { debounceClick { musicService?.playNext() } }
        binding.miniPrevious.setOnClickListener  { debounceClick { musicService?.playPrevious() } }
    }

    // ---- Ricerca ----

    private fun showSearchResults(query: String) {
        val tokens = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val allSongs   = libraryViewModel.songs.value   ?: emptyList()
        val allArtists = libraryViewModel.artists.value ?: emptyList()
        val allAlbums  = libraryViewModel.albums.value  ?: emptyList()

        val matchedSongs = allSongs.filter { song ->
            tokens.all { token -> song.title.lowercase().contains(token) }
        }
        val matchedArtists = allArtists.filter { artist ->
            tokens.all { token -> artist.name.lowercase().contains(token) }
        }
        val matchedAlbums = allAlbums.filter { album ->
            tokens.all { token -> album.title.lowercase().contains(token) }
        }

        val results = mutableListOf<SearchResult>()
        if (matchedSongs.isNotEmpty()) {
            results.add(SearchResult.Header("Brani"))
            matchedSongs.forEach { results.add(SearchResult.SongResult(it)) }
        }
        if (matchedArtists.isNotEmpty()) {
            results.add(SearchResult.Header("Artisti"))
            matchedArtists.forEach { results.add(SearchResult.ArtistResult(it.name)) }
        }
        if (matchedAlbums.isNotEmpty()) {
            results.add(SearchResult.Header("Album"))
            matchedAlbums.forEach { results.add(SearchResult.AlbumResult(it)) }
        }

        searchAdapter.submitList(results)
        binding.recyclerViewSearch.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
    }

    private fun closeSearch() {
        // Disabilita il callback: il tasto indietro torna al comportamento normale
        searchBackCallback.isEnabled = false
        binding.tilSearch.visibility = View.GONE
        binding.etSearch.text?.clear()
        binding.recyclerViewSearch.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        binding.tabLayout.visibility = View.VISIBLE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    // ---- Caricamento ----

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

    private var isFirstScan = true

    private fun loadSongs() {
        Thread {
            val cached = SongCache.load(this)
            if (cached != null) {
                isFirstScan = false
                runOnUiThread { applySongs(cached) }
            } else {
                runOnUiThread { binding.progressScan.visibility = View.VISIBLE }
            }
            scanSongs()
        }.start()
    }

    override fun scanSongs(onComplete: ((List<Song>) -> Unit)?) {
        if (isScanning) return
        isScanning = true

        if (isFirstScan) {
            runOnUiThread { binding.progressScan.visibility = View.VISIBLE }
        }

        Thread {
            val result = SongRepository.getAllSongs(this)
            SongCache.save(this, result)
            runOnUiThread {
                applySongs(result)
                binding.progressScan.visibility = View.GONE
                isScanning = false
                isFirstScan = false
                onComplete?.invoke(result)
            }
        }.start()
    }

    private fun applySongs(result: List<Song>) {
        val sorted = result.sortedBy { it.title.sortKey() }
        binding.tvSongCount.text = getString(R.string.song_count, sorted.size)
        binding.tvSongCount.visibility = View.VISIBLE
        libraryViewModel.setSongs(sorted)
    }

    // ---- Mini player ----

    override fun showMiniPlayer(song: Song) {
        binding.miniPlayer.visibility = View.VISIBLE
        binding.miniTitle.text  = song.title
        binding.miniArtist.text = song.artist
        updatePlayPauseIcon(musicService?.isPlaying ?: false)

        Thread {
            val bmp = SongRepository.getAlbumArt(this, song)
            runOnUiThread {
                if (bmp != null) binding.miniAlbumArt.setImageBitmap(bmp)
                else binding.miniAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }.start()
    }

    override fun updatePlayPauseIcon(playing: Boolean) {
        binding.miniPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_white_24dp
            else R.drawable.ic_play_white_24dp
        )
    }

    override fun updateRandomIcon() {
        binding.miniRandom.setImageResource(
            if (musicService?.isShuffleEnabled == true) R.drawable.ic_random_white_24dp
            else R.drawable.ic_random_off_white_24dp
        )
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
}
