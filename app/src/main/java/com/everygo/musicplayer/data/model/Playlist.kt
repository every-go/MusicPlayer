package com.everygo.musicplayer

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList()
)