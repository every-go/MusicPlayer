package com.everygo.musicplayer

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: String,
    val trackNumber: Int,
    val genre: String,
    val duration: Long,
    val uri: Uri
) {
    fun formattedDuration(): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return "%d:%02d".format(minutes, seconds)
    }
}