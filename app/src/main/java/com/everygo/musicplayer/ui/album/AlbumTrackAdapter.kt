package com.everygo.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ItemAlbumTrackBinding

class AlbumTrackAdapter(
    private val onTrackClick: (Song) -> Unit,
    private val onOverflowClick: (Song, android.view.View) -> Unit
) : ListAdapter<Song, AlbumTrackAdapter.ViewHolder>(DiffCallback) {

    private var currentSongId: Long = -1

    fun setCurrentSong(songId: Long) {
        val old = currentList.indexOfFirst { it.id == currentSongId }
        val new = currentList.indexOfFirst { it.id == songId }
        currentSongId = songId
        if (old != -1) notifyItemChanged(old)
        if (new != -1) notifyItemChanged(new)
    }

    inner class ViewHolder(private val binding: ItemAlbumTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTrackNumber.text = if (song.trackNumber > 0)
                song.trackNumber.toString() else "–"
            binding.tvTrackTitle.text = song.title
            binding.tvTrackArtist.text = song.artist

            val isCurrent = song.id == currentSongId
            binding.tvTrackTitle.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvTrackArtist.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFAAAAAA.toInt()
            )
            binding.tvTrackNumber.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFAAAAAA.toInt()
            )
            binding.root.setBackgroundColor(
                if (isCurrent) 0xFF2A2A3A.toInt() else 0x00000000
            )

            binding.root.setOnClickListener { onTrackClick(song) }
            binding.btnTrackOverflow.setOnClickListener { onOverflowClick(song, it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
    }
}