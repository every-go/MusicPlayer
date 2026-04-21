package com.everygo.musicplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _artists = MutableLiveData<List<Artist>>(emptyList())
    val artists: LiveData<List<Artist>> = _artists

    private val _albums = MutableLiveData<List<Album>>(emptyList())
    val albums: LiveData<List<Album>> = _albums

    private val _playlists = MutableLiveData<List<Playlist>>(emptyList())
    val playlists: LiveData<List<Playlist>> = _playlists

    fun setSongs(songs: List<Song>) {
        _songs.value = songs
        _artists.value = SongRepository.loadAllArtists(songs)
        _albums.value = SongRepository.loadAllAlbums(songs)
    }

    fun loadPlaylists() {
        _playlists.value = PlaylistRepository.load(getApplication())
    }

    fun refreshPlaylists() {
        _playlists.value = PlaylistRepository.load(getApplication())
    }
}