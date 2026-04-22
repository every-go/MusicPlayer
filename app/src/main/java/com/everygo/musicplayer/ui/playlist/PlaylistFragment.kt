package com.everygo.musicplayer

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.everygo.musicplayer.databinding.FragmentPlaylistBinding

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                val intent = Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
                    putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.id)
                }
                startActivity(intent)
            },
            onRenameClick = { playlist -> showRenameDialog(playlist) },
            onDeleteClick = { playlist ->
                PlaylistRepository.delete(requireContext(), playlist.id)
                libraryViewModel.refreshPlaylists()
            }
        )

        binding.recyclerViewPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPlaylists.adapter = adapter
        binding.recyclerViewPlaylists.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(requireContext(), androidx.recyclerview.widget.DividerItemDecoration.VERTICAL)
        )

        libraryViewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.submitList(playlists)
            binding.tvEmptyPlaylists.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnNewPlaylist.setOnClickListener { showCreateDialog() }

        libraryViewModel.loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.refreshPlaylists()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun makeEditText(prefill: String? = null): EditText {
        val d = resources.displayMetrics.density
        val pH = (16 * d).toInt()
        val pV = (8 * d).toInt()

        return EditText(requireContext()).apply {
            if (prefill != null) setText(prefill)

            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())

            background = GradientDrawable().apply {
                setColor(0xFF2D2D2D.toInt())
                cornerRadius = 14 * d
                setStroke(2, 0xFF444444.toInt())
            }

            isSingleLine = true
            includeFontPadding = false

            setPadding(pH, pV, pH, pV)
        }
    }

    private fun showCreateDialog() {
        val input = makeEditText().apply {
            hint = "Nome playlist"
            minHeight = (40 * resources.displayMetrics.density).toInt()
        }

        val titleView = TextView(requireContext()).apply {
            text = getString(R.string.new_playlist)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p / 2)
        }

        AlertDialog.Builder(requireContext())
            .setCustomTitle(titleView)
            .setView(input)
            .setPositiveButton("Crea") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    PlaylistRepository.create(requireContext(), name)
                    libraryViewModel.refreshPlaylists()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showRenameDialog(playlist: Playlist) {
        val input = makeEditText(playlist.name)
        AlertDialog.Builder(requireContext())
            .setTitle("Rinomina playlist")
            .setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    PlaylistRepository.rename(requireContext(), playlist.id, name)
                    libraryViewModel.refreshPlaylists()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
