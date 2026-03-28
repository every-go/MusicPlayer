package com.everygo.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ItemPlaylistTrackBinding

class PlaylistTrackAdapter(
    private val onTrackClick: (Song) -> Unit,
    private val onMenuClick: (Song, View) -> Unit,   // nuovo callback
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : ListAdapter<Song, PlaylistTrackAdapter.ViewHolder>(DiffCallback) {

    private var currentSongId: Long = -1

    fun setCurrentSong(songId: Long) {
        val old = currentList.indexOfFirst { it.id == currentSongId }
        val new = currentList.indexOfFirst { it.id == songId }
        currentSongId = songId
        if (old != -1) notifyItemChanged(old)
        if (new != -1) notifyItemChanged(new)
    }

    fun moveItem(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        submitList(list)
    }

    inner class ViewHolder(private val binding: ItemPlaylistTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTrackTitle.text = song.title
            binding.tvTrackArtist.text = song.artist

            val isCurrent = song.id == currentSongId
            binding.tvTrackTitle.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvTrackArtist.setTextColor(
                if (isCurrent) 0xFFBB86FC.toInt() else 0xFFAAAAAA.toInt()
            )

            binding.root.setOnClickListener { onTrackClick(song) }

            // Pulsante menu
            binding.btnSongMenu.setOnClickListener { anchor ->
                onMenuClick(song, anchor)
            }

            // Drag handle
            binding.ivDragHandle.setOnLongClickListener {
                onDragStart(this@ViewHolder)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistTrackBinding.inflate(
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