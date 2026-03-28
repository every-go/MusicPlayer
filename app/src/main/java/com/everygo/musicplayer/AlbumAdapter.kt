package com.everygo.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.everygo.musicplayer.databinding.ItemAlbumBinding

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.tvAlbumName.text = album.title
            binding.ivAlbumCover.setImageResource(R.drawable.ic_launcher_foreground)

            val firstSong = album.songs.firstOrNull()
            if (firstSong != null) {
                Thread {
                    val bmp = SongRepository.getAlbumArt(binding.root.context, firstSong)
                    binding.root.post {
                        if (bmp != null) binding.ivAlbumCover.setImageBitmap(bmp)
                    }
                }.start()
            }

            binding.root.setOnClickListener { onAlbumClick(album) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album) =
            oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: Album, newItem: Album) =
            oldItem == newItem
    }
}