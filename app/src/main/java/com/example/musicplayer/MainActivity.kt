package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.ActivityMainBinding
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.IntentSenderRequest
import android.provider.MediaStore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    private var musicService: MusicService? = null
    private var isBound = false

    private var isScanning = false

    // ---- Listener dichiarati come proprietà per poterli rimuovere ----
    private val songChangedListener: (Song) -> Unit = { song ->
        runOnUiThread {
            showMiniPlayer(song)
            adapter.setCurrentSong(song.id)   // ← aggiunta
        }
    }
    private val playbackStateListener: (Boolean) -> Unit = { playing ->
        runOnUiThread { updatePlayPauseIcon(playing) }
    }

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            scanSongs()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicService.MusicBinder).getService()
            isBound = true

            musicService!!.currentSong?.let { showMiniPlayer(it) }
            updateRandomIcon()

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

        // Rimuove la barra con il nome dell'app
        supportActionBar?.hide()

        setupRecyclerView()
        setupMiniPlayer()
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

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.etSearch.isVisible) {
                    closeSearch()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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

    override fun onResume() {
        super.onResume()
        updateRandomIcon()
    }

    // ---- Setup ----

    private fun setupHeader() {

        binding.btnSearch.setOnClickListener {
            if (binding.etSearch.isGone) {
                binding.etSearch.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                closeSearch()
            }
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSongs(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnPlayAll.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            musicService?.let {
                it.playlist = songs
                it.playSong((0 until songs.size).random())
                PlayerActivity.pendingPlaylist = songs
            }
        }

        // 3 puntini → PopupMenu
        binding.btnOverflow.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_scan -> { scanSongs(); true }
                    else             -> false
                }
            }
            popup.show()
        }
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
                deleteSong(song)
            }
        )
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

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            scanSongs()
        }
    }

    private fun deleteSong(song: Song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(contentResolver, listOf(song.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            contentResolver.delete(song.uri, null, null)
            scanSongs()
        }
    }

    // ---- Caricamento canzoni ----

    private fun loadSongs() {
        binding.progressScan.visibility = View.VISIBLE
        Thread {
            val cached = SongCache.load(this)
            if (cached != null) {
                runOnUiThread {
                    binding.progressScan.visibility = View.GONE
                    applySongs(cached) }
            } else {
                scanSongs()
            }
        }.start()
    }

    private fun scanSongs() {
        if (isScanning) return
        isScanning = true

        Thread {
            val result = SongRepository.getAllSongs(this)
            SongCache.save(this, result)
            runOnUiThread {
                applySongs(result)
                binding.progressScan.visibility = View.GONE
                isScanning = false
            }
        }.start()
    }

    private fun applySongs(result: List<Song>) {
        songs = result.sortedBy { it.title.sortKey() }
        adapter.submitList(songs)
        binding.tvSongCount.text = getString(R.string.song_count, songs.size)
        binding.tvSongCount.visibility = View.VISIBLE
        binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE

        binding.alphabetScrollbar.onLetterSelected = { letter ->
            binding.tvLetterPopup.text = letter
            binding.tvLetterPopup.visibility = View.VISIBLE
            binding.tvLetterPopup.removeCallbacks(hidePopup)
            binding.tvLetterPopup.postDelayed(hidePopup, 800)

            val letters = listOf("#") + ('A'..'Z').map { it.toString() }
            val startPos = letters.indexOf(letter)

            // Cerca dalla lettera selezionata in poi la prima con almeno un brano
            val index = letters.drop(startPos).firstNotNullOfOrNull { l ->
                val i = if (l == "#") {
                    songs.indexOfFirst {
                        val first = it.title.firstOrNull()?.normalize()
                        first == null || !first.isLetter()
                    }
                } else {
                    songs.indexOfFirst {
                        it.title.firstOrNull()?.normalize()?.uppercaseChar()?.toString() == l
                    }
                }
                if (i != -1) i else null
            }

            if (index != null) {
                (binding.recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(index, 0)
            } else {
                (binding.recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(songs.size - 1, 0)
            }
        }
    }

    // ---- Mini player ----

    private fun setupMiniPlayer() {
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

    private val hidePopup = Runnable { binding.tvLetterPopup.visibility = View.GONE }

    private var lastClickTime = 0L
    private val clickDebounce = 500L

    private fun debounceClick(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime >= clickDebounce) {
            lastClickTime = now
            action()
        }
    }

    private fun showMiniPlayer(song: Song) {
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

    // --- Ricerca ---

    private fun filterSongs(query: String) {
        val tokens = query.trim().lowercase()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }

        val filtered = if (tokens.isEmpty()) songs else songs.filter { song ->
            tokens.all { token ->
                song.title.lowercase().contains(token)  ||
                        song.artist.lowercase().contains(token) ||
                        song.album.lowercase().contains(token)
            }
        }
        adapter.submitList(filtered)
    }

    private fun closeSearch() {
        binding.etSearch.visibility = View.GONE
        binding.etSearch.text.clear()
        adapter.submitList(songs)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}