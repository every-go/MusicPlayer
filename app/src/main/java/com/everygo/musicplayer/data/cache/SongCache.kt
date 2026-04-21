package com.everygo.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.core.net.toUri

object SongCache {

    private const val FILE_NAME = "song_cache.json"

    fun save(context: Context, songs: List<Song>) {
        val array = JSONArray()

        songs.forEach { song ->
            array.put(JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("artist", song.artist)
                put("albumArtist", song.albumArtist)
                put("album", song.album)
                put("year", song.year)
                put("trackNumber", song.trackNumber)
                put("genre", song.genre)
                put("duration", song.duration)
                put("uri", song.uri.toString())
            })
        }

        cacheFile(context).writeText(array.toString())
    }

    fun load(context: Context): List<Song>? {
        val file = cacheFile(context)
        if (!file.exists()) return null

        return try {
            val array = JSONArray(file.readText())

            List(array.length()) { i ->
                val o = array.getJSONObject(i)

                val uriString = o.optString("uri", "")
                if (uriString.isEmpty()) return@List null

                Song(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    albumArtist = o.optString("albumArtist"),
                    album = o.optString("album"),
                    year = o.optString("year"),
                    trackNumber = o.optInt("trackNumber"),
                    genre = o.optString("genre"),
                    duration = o.optLong("duration"),
                    uri = uriString.toUri()
                )
            }.filterNotNull()

        } catch (_: Exception) {
            null
        }
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}