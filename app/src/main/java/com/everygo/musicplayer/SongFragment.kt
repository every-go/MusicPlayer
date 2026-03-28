package com.everygo.musicplayer

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.everygo.musicplayer.databinding.FragmentSongsBinding

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val musicService get() = (activity as? MainActivity)?.musicService

    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    private val editMetadataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            (activity as? MainActivity)?.reloadFromCache()
        }
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            (activity as? MainActivity)?.scanSongs()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        // Osserva i brani — si aggiorna automaticamente quando arrivano
        libraryViewModel.songs.observe(viewLifecycleOwner) { newSongs ->
            songs = newSongs
            applyCurrentFilter()
            setupAlphabetScrollbar()
            binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Setup ----

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
                val intent = Intent(requireContext(), EditMetadataActivity::class.java).apply {
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
            onDeleteClick = { song -> deleteSong(song) },
            onPlayNextClick = { song ->
                (activity as? MainActivity)?.musicService?.playNextSong(song)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                requireContext(),
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            )
        )
    }

    private fun setupAlphabetScrollbar() {
        binding.alphabetScrollbar.onLetterSelected = { letter ->
            binding.tvLetterPopup.text = letter
            binding.tvLetterPopup.visibility = View.VISIBLE
            binding.tvLetterPopup.removeCallbacks(hidePopup)
            binding.tvLetterPopup.postDelayed(hidePopup, 800)

            val letters = listOf("#") + ('A'..'Z').map { it.toString() }
            val startPos = letters.indexOf(letter)

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

    // ---- Ricerca ----

    private var currentQuery: String = ""

    fun setCurrentSong(songId: Long) {
        adapter.setCurrentSong(songId)
    }

    private fun applyCurrentFilter() {
        val tokens = currentQuery.trim().lowercase()
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

    // ---- Delete ----

    private fun deleteSong(song: Song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createDeleteRequest(
                requireContext().contentResolver,
                listOf(song.uri)
            )
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            requireContext().contentResolver.delete(song.uri, null, null)
            (activity as? MainActivity)?.scanSongs()
        }
    }

    private val hidePopup = Runnable {
        binding.tvLetterPopup.visibility = View.GONE
    }
}