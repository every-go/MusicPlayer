package com.everygo.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.core.net.toUri

/**
 * Persiste la lista di Song su file JSON nella cache interna dell'app.
 * Nessuna dipendenza esterna — usa org.json già incluso in Android.
 *
 * Flusso:
 *   - All'avvio: SongCache.load(context) → lista immediata, nessuna scansione
 *   - Su richiesta utente: SongRepository.getAllSongs(context) → SongCache.save(context, songs)
 */
object SongCache {

    private const val FILE_NAME = "song_cache.json"

    // ---- Scrittura ----

    fun save(context: Context, songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(JSONObject().apply {
                put("id",          song.id)
                put("title",       song.title)
                put("artist",      song.artist)
                put("albumArtist", song.albumArtist)
                put("album",       song.album)
                put("year",        song.year)
                put("trackNumber", song.trackNumber)
                put("genre",       song.genre)
                put("duration",    song.duration)
                put("uri",         song.uri.toString())
            })
        }
        cacheFile(context).writeText(array.toString())
        android.util.Log.d("MusicPlayer", "Cache salvata: ${songs.size} brani")
    }

    // ---- Lettura ----

    /**
     * Ritorna la lista salvata, oppure null se la cache non esiste ancora.
     */
    fun load(context: Context): List<Song>? {
        val file = cacheFile(context)
        if (!file.exists()) return null

        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Song(
                    id          = o.getLong("id"),
                    title       = o.getString("title"),
                    artist      = o.getString("artist"),
                    albumArtist = o.getString("albumArtist"),
                    album       = o.getString("album"),
                    year        = o.getString("year"),
                    trackNumber = o.getInt("trackNumber"),
                    genre       = o.getString("genre"),
                    duration    = o.getLong("duration"),
                    uri         = o.getString("uri").toUri()
                )
            }.also {
                android.util.Log.d("MusicPlayer", "Cache caricata: ${it.size} brani")
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicPlayer", "Cache corrotta, verrà ignorata: ${e.message}")
            null
        }
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, FILE_NAME)
}