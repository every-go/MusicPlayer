package com.example.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onRenameClick: (Playlist) -> Unit,
    private val onDeleteClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.tvPlaylistName.text  = playlist.name
            binding.tvPlaylistCount.text = "${playlist.songIds.size} brani"
            binding.root.setOnClickListener { onPlaylistClick(playlist) }
            binding.btnPlaylistOverflow.setOnClickListener { view ->
                val popup = android.widget.PopupMenu(view.context, view)
                popup.menu.add(0, 0, 0, "Rinomina")
                popup.menu.add(0, 1, 1, "Elimina")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        0 -> { onRenameClick(playlist); true }
                        1 -> {
                            android.app.AlertDialog.Builder(view.context)
                                .setTitle("Elimina playlist")
                                .setMessage("Vuoi eliminare \"${playlist.name}\"?")
                                .setPositiveButton("Elimina") { _, _ -> onDeleteClick(playlist) }
                                .setNegativeButton("Annulla", null)
                                .show()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem == newItem
    }
}