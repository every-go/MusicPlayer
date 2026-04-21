package com.everygo.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class SearchAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onArtistClick: (String) -> Unit,
    private val onAlbumClick: (Album) -> Unit,
    private val onSongMenuClick: (Song, View) -> Unit
) : ListAdapter<SearchResult, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SONG   = 1
        private const val TYPE_ARTIST = 2
        private const val TYPE_ALBUM  = 3

        val DiffCallback = object : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
                return when {
                    oldItem is SearchResult.Header && newItem is SearchResult.Header -> oldItem.title == newItem.title
                    oldItem is SearchResult.SongResult && newItem is SearchResult.SongResult -> oldItem.song.id == newItem.song.id
                    oldItem is SearchResult.ArtistResult && newItem is SearchResult.ArtistResult -> oldItem.name == newItem.name
                    oldItem is SearchResult.AlbumResult && newItem is SearchResult.AlbumResult -> oldItem.album.title == newItem.album.title
                    else -> false
                }
            }
            override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult) = oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is SearchResult.Header       -> TYPE_HEADER
        is SearchResult.SongResult   -> TYPE_SONG
        is SearchResult.ArtistResult -> TYPE_ARTIST
        is SearchResult.AlbumResult  -> TYPE_ALBUM
    }

    // ---- ViewHolders ----

    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvHeader: TextView = v.findViewById(R.id.tvSearchHeader)
        fun bind(item: SearchResult.Header) { tvHeader.text = item.title }
    }

    inner class SongVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView  = v.findViewById(R.id.tvSearchTitle)
        val tvSub: TextView    = v.findViewById(R.id.tvSearchSubtitle)
        val btnMenu: MaterialButton = v.findViewById(R.id.btnSearchSongMenu)

        fun bind(item: SearchResult.SongResult) {
            tvTitle.text = item.song.title
            tvSub.text   = item.song.artist
            itemView.setOnClickListener { onSongClick(item.song) }
            btnMenu.setOnClickListener { onSongMenuClick(item.song, it) }
        }
    }

    inner class ArtistVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvSearchTitle)
        val tvSub: TextView   = v.findViewById(R.id.tvSearchSubtitle)
        fun bind(item: SearchResult.ArtistResult) {
            tvTitle.text = item.name
            tvSub.text   = itemView.context.getString(R.string.label_artist)
            itemView.setOnClickListener { onArtistClick(item.name) }
        }
    }

    inner class AlbumVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvSearchTitle)
        val tvSub: TextView   = v.findViewById(R.id.tvSearchSubtitle)
        fun bind(item: SearchResult.AlbumResult) {
            tvTitle.text = item.album.title
            tvSub.text   = itemView.context.getString(R.string.label_album)
            itemView.setOnClickListener { onAlbumClick(item.album) }
        }
    }

    // ---- Inflate ----

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_search_header, parent, false))
            TYPE_SONG   -> SongVH(inflater.inflate(R.layout.item_search_result_song, parent, false))
            TYPE_ARTIST -> ArtistVH(inflater.inflate(R.layout.item_search_result, parent, false))
            TYPE_ALBUM  -> AlbumVH(inflater.inflate(R.layout.item_search_result, parent, false))
            else        -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchResult.Header       -> (holder as HeaderVH).bind(item)
            is SearchResult.SongResult   -> (holder as SongVH).bind(item)
            is SearchResult.ArtistResult -> (holder as ArtistVH).bind(item)
            is SearchResult.AlbumResult  -> (holder as AlbumVH).bind(item)
        }
    }
}
