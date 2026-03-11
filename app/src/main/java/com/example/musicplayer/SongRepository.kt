package com.example.musicplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever

/**
 * Legge le canzoni direttamente da MediaStore (i tag ID3 dei file audio).
 * Nessun DB custom — Android indicizza già tutto.
 */
object SongRepository {

    fun getAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        // Prima carica tutti i generi in una mappa id→genere
        val genreMap = loadAllGenres(context)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            android.util.Log.d("MusicPlayer", "Righe trovate: ${cursor.count}")

            val idCol          = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val albumCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val yearCol        = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durationCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                songs.add(Song(
                    id          = id,
                    title       = cursor.getString(titleCol)       ?: "Sconosciuto",
                    artist      = cursor.getString(artistCol)      ?: "Artista sconosciuto",
                    albumArtist = cursor.getString(albumArtistCol) ?: "",
                    album       = cursor.getString(albumCol)       ?: "Album sconosciuto",
                    year        = cursor.getString(yearCol)        ?: "",
                    trackNumber = cursor.getInt(trackCol),
                    genre       = genreMap[id] ?: "",   // ← dalla mappa, non da query separata
                    duration    = cursor.getLong(durationCol),
                    uri         = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                ))
            }
        }

        android.util.Log.d("MusicPlayer", "Canzoni totali: ${songs.size}")
        return songs
    }

    // Una sola query per TUTTI i generi → mappa songId → genere
    private fun loadAllGenres(context: Context): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        try {
            val genres = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                genres,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )?.use { genreCursor ->
                val gIdCol   = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val gNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId   = genreCursor.getLong(gIdCol)
                    val genreName = genreCursor.getString(gNameCol) ?: continue
                    // Per ogni genere, carica i suoi brani
                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    context.contentResolver.query(
                        membersUri,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { membersCursor ->
                        val audioIdCol = membersCursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (membersCursor.moveToNext()) {
                            map[membersCursor.getLong(audioIdCol)] = genreName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicPlayer", "Generi non disponibili: ${e.message}")
        }
        return map
    }

    /**
     * Carica la copertina dell'album come Bitmap.
     * Restituisce null se non disponibile.
     */
    fun getAlbumArt(context: Context, songUri: Uri): android.graphics.Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(songUri, android.util.Size(300, 300), null)
            } else {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, songUri)
                val artBytes = mmr.embeddedPicture
                mmr.release()
                if (artBytes != null)
                    android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                else null
            }
        } catch (e: Exception) {
            null
        }
    }
}