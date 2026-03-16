package com.example.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.databinding.ItemSongBinding

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentSongId: Long = -1

    fun setCurrentSong(songId: Long) {
        val old = currentList.indexOfFirst { it.id == currentSongId }
        val new = currentList.indexOfFirst { it.id == songId }
        currentSongId = songId
        if (old != -1) notifyItemChanged(old)   // deseleziona il vecchio
        if (new != -1) notifyItemChanged(new)   // evidenzia il nuovo
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding   // ← generato da item_song.xml
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            binding.tvTitle.text    = song.title
            binding.tvArtist.text   = song.artist
            binding.tvDuration.text = song.formattedDuration()

            val isCurrent = song.id == currentSongId
            binding.tvTitle.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt()   // viola accent
                else           0xFFFFFFFF.toInt()    // bianco normale
            )
            binding.tvArtist.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt()
                else           0xFFAAAAAA.toInt()
            )
            binding.root.setBackgroundColor(
                if (isCurrent) 0xFF2A2A3A.toInt()   // sfondo leggermente diverso
                else           0x00000000.toInt()    // trasparente
            )

            binding.root.setOnClickListener { onSongClick(song, position) }
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

    // DiffUtil evita di ridisegnare tutta la lista quando cambia un solo elemento
    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(old: Song, new: Song) = old.id == new.id
        override fun areContentsTheSame(old: Song, new: Song) = old == new
    }
}