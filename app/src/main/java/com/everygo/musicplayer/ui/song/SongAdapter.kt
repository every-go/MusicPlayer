package com.everygo.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ItemSongBinding

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onEditClick: (Song) -> Unit,
    private val onDeleteClick: (Song) -> Unit,
    private val onPlayNextClick: ((Song) -> Unit)? = null,
    private val currentArtistFilter: String? = null
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentSongId: Long = -1

    fun setCurrentSong(songId: Long) {
        val old = currentList.indexOfFirst { it.id == currentSongId }
        val new = currentList.indexOfFirst { it.id == songId }
        currentSongId = songId
        if (old != -1) notifyItemChanged(old)
        if (new != -1) notifyItemChanged(new)
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            binding.tvTitle.text    = song.title
            binding.tvArtist.text   = song.artist
            binding.tvDuration.text = song.formattedDuration()

            val isCurrent = song.id == currentSongId
            binding.tvTitle.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvArtist.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFAAAAAA.toInt()
            )
            binding.root.setBackgroundColor(
                if (isCurrent) 0xFF2A2A3A.toInt() else 0x00000000
            )

            binding.root.setOnClickListener { onSongClick(song, position) }

            binding.btnSongMenu.setOnClickListener { anchor ->
                val artistNames = song.artist.split(",").map { it.trim() }
                val otherArtists = if (currentArtistFilter != null) {
                    artistNames.filter {
                        !it.equals(currentArtistFilter, ignoreCase = true) &&
                                !it.fixEncoding().equals(currentArtistFilter, ignoreCase = true)
                    }
                } else {
                    artistNames // se non c'è filtro, tutti gli artisti sono "alternativi"
                }

                val popup = android.widget.PopupMenu(anchor.context, anchor)
                val menu = popup.menu

                // Voci standard
                menu.add(0, 0, 0, "Riproduci dopo")
                menu.add(0, 1, 1, "Aggiungi a playlist")
                menu.add(0, 2, 2, "Modifica")
                menu.add(0, 3, 3, "Elimina")

                // Gestione "Vai all'artista" con sottomenu se ci sono artisti alternativi
                if (otherArtists.isNotEmpty()) {
                    val subMenu = menu.addSubMenu(0, 4, 4, "Vai all'artista")
                    otherArtists.forEachIndexed { index, artist ->
                        subMenu.add(0, 100 + index, index, artist)
                    }
                }

                // Voce "Vai all'album"
                menu.add(0, 5, 5, "Vai all'album")

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        0 -> { // Riproduci dopo
                            onPlayNextClick?.invoke(song)
                            true
                        }
                        1 -> { // Aggiungi a playlist
                            val playlists = PlaylistRepository.load(anchor.context)
                            val playlistNames = playlists.map { it.name }.toTypedArray()
                            android.app.AlertDialog.Builder(anchor.context)
                                .setTitle("Aggiungi a playlist")
                                .setItems(playlistNames) { _, which ->
                                    val selectedPlaylist = playlists[which]
                                    PlaylistRepository.addSong(anchor.context, selectedPlaylist.id, song.id)
                                }
                                .setNegativeButton("Annulla", null)
                                .show()
                            true
                        }
                        2 -> { // Modifica
                            onEditClick(song)
                            true
                        }
                        3 -> { // Elimina
                            onDeleteClick(song)
                            true
                        }
                        in 100..199 -> { // Sottomenu artisti
                            val artist = otherArtists[item.itemId - 100]
                            val intent = android.content.Intent(anchor.context, ArtistDetailActivity::class.java).apply {
                                putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artist)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            anchor.context.startActivity(intent)
                            true
                        }
                        5 -> { // Vai all'album
                            val intent = android.content.Intent(anchor.context, AlbumDetailActivity::class.java).apply {
                                putExtra(AlbumDetailActivity.EXTRA_ALBUM_NAME, song.album)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            anchor.context.startActivity(intent)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(old: Song, new: Song) = old.id == new.id
        override fun areContentsTheSame(old: Song, new: Song) = old == new
    }
}