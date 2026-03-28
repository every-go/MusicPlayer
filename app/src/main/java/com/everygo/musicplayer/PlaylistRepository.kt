package com.everygo.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PlaylistRepository {

    private const val FILE_NAME = "playlists.json"

    fun load(context: Context): List<Playlist> {
        return try {
            val file = java.io.File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            val json = JSONArray(file.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                val ids = obj.getJSONArray("songIds")
                Playlist(
                    id      = obj.getString("id"),
                    name    = obj.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        try {
            val json = JSONArray()
            playlists.forEach { playlist ->
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("name", playlist.name)
                val ids = JSONArray()
                playlist.songIds.forEach { ids.put(it) }
                obj.put("songIds", ids)
                json.put(obj)
            }
            java.io.File(context.filesDir, FILE_NAME).writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun create(context: Context, name: String): Playlist {
        val playlists = load(context).toMutableList()
        val new = Playlist(name = name)
        playlists.add(new)
        save(context, playlists)
        return new
    }

    fun rename(context: Context, id: String, newName: String) {
        val playlists = load(context).map {
            if (it.id == id) it.copy(name = newName) else it
        }
        save(context, playlists)
    }

    fun delete(context: Context, id: String) {
        val playlists = load(context).filter { it.id != id }
        save(context, playlists)
    }

    fun addSong(context: Context, playlistId: String, songId: Long) {
        val playlists = load(context).map { playlist ->
            if (playlist.id == playlistId && !playlist.songIds.contains(songId)) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else playlist
        }
        save(context, playlists)
    }

    fun removeSong(context: Context, playlistId: String, songId: Long) {
        val playlists = load(context).map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(songIds = playlist.songIds.filter { it != songId })
            } else playlist
        }
        save(context, playlists)
    }

    fun reorder(context: Context, playlistId: String, newOrder: List<Long>) {
        val playlists = load(context).map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songIds = newOrder)
            else playlist
        }
        save(context, playlists)
    }
}