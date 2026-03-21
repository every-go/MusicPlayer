package com.example.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentAlbumBinding

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: AlbumAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        libraryViewModel.albums.observe(viewLifecycleOwner) { albums ->
            val titles = albums.map { it.title }
            adapter.submitList(albums)
            binding.tvEmptyAlbums.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
            setupAlphabetScrollbar(titles)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        adapter = AlbumAdapter { album ->
            val intent = Intent(requireContext(), AlbumDetailActivity::class.java).apply {
                putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, album.title)
            }
            startActivity(intent)
        }
        binding.recyclerViewAlbums.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewAlbums.adapter = adapter
        binding.recyclerViewAlbums.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                requireContext(),
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            )
        )
    }

    private fun setupAlphabetScrollbar(items: List<String>) {
        binding.alphabetScrollbar.onLetterSelected = { letter ->
            binding.tvLetterPopup.text = letter
            binding.tvLetterPopup.visibility = View.VISIBLE
            binding.tvLetterPopup.removeCallbacks(hidePopup)
            binding.tvLetterPopup.postDelayed(hidePopup, 800)

            val letters = listOf("#") + ('A'..'Z').map { it.toString() }
            val startPos = letters.indexOf(letter)

            val index = letters.drop(startPos).firstNotNullOfOrNull { l ->
                val i = if (l == "#") {
                    items.indexOfFirst {
                        val first = it.firstOrNull()?.normalize()
                        first == null || !first.isLetter()
                    }
                } else {
                    items.indexOfFirst {
                        it.firstOrNull()?.normalize()?.uppercaseChar()?.toString() == l
                    }
                }
                if (i != -1) i else null
            }

            if (index != null) {
                (binding.recyclerViewAlbums.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(index, 0)
            } else {
                (binding.recyclerViewAlbums.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(items.size - 1, 0)
            }
        }
    }

    private val hidePopup = Runnable {
        binding.tvLetterPopup.visibility = View.GONE
    }
}