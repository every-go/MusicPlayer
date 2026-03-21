package com.example.musicplayer

sealed class SearchResult {
    data class Header(val title: String) : SearchResult()
    data class SongResult(val song: Song) : SearchResult()
    data class ArtistResult(val name: String) : SearchResult()
    data class AlbumResult(val album: Album) : SearchResult()
}