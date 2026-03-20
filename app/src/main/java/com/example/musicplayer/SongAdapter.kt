package com.example.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.databinding.ItemSongBinding

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onEditClick: (Song) -> Unit,
    private val onDeleteClick: (Song) -> Unit
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
                if (isCurrent) 0xFFBB86FC.toInt()
                else           0xFFFFFFFF.toInt()
            )
            binding.tvArtist.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt()
                else           0xFFAAAAAA.toInt()
            )
            binding.root.setBackgroundColor(
                if (isCurrent) 0xFF2A2A3A.toInt()
                else 0x00000000
            )

            binding.root.setOnClickListener {
                onSongClick(song, position)
            }

            binding.btnSongMenu.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menuInflater.inflate(R.menu.menu_song_item, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_metadata -> { onEditClick(song); true }
                        R.id.action_delete_file -> { onDeleteClick(song); true }
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