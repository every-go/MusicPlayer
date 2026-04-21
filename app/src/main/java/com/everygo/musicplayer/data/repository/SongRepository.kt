package com.everygo.musicplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever

// ---- Data class di supporto per le viste Artist / Album / Genre ----

data class Artist(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int = songs.map { it.album }.distinct().count()
)

data class Album(
    val title: String,
    val artist: String,
    val year: String,
    val songs: List<Song>,
)

/**
 * Legge le canzoni direttamente da MediaStore (i tag ID3 dei file audio).
 * Nessun DB custom — Android indicizza già tutto.
 *
 * loadAllArtists / loadAllAlbums / loadAllGenres / getAlbumArt lavorano
 * sulla List<Song> già in memoria — nessuna query aggiuntiva a MediaStore.
 */
object SongRepository {

    // Regex: cattura tutto ciò che precede la prima virgola.
    // "Sfera Ebbasta, Capo Plaza" → "Sfera Ebbasta"
    // "Drake"                     → "Drake"
    private val firstArtistRegex = Regex("""^([^,]+)""")

    private fun extractFirstArtist(raw: String): String =
        firstArtistRegex.find(raw.trim())?.value?.trim() ?: raw.trim()

    // ---- Canzoni (MediaStore) ----

    fun getAllSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA, // percorso fisico del file
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
            null
        )?.use { cursor ->
            android.util.Log.d("MusicPlayer", "Righe trovate: ${cursor.count}")

            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id       = cursor.getLong(idCol)
                val path     = cursor.getString(dataCol) ?: continue
                val duration = cursor.getLong(durationCol)
                val uri      = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                try {
                    val file = java.io.File(path)
                    if (!file.exists()) continue

                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                    val tag = audioFile.tag

                    val title       = tag?.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                    val artist      = tag?.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "Artista sconosciuto"
                    val albumArtist = tag?.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST) ?: ""
                    val album       = tag?.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: "Album sconosciuto"
                    val year        = tag?.getFirst(org.jaudiotagger.tag.FieldKey.YEAR) ?: ""
                    val trackNumber = tag?.getFirst(org.jaudiotagger.tag.FieldKey.TRACK)?.toIntOrNull() ?: 0
                    val genre       = tag?.getFirst(org.jaudiotagger.tag.FieldKey.GENRE) ?: ""

                    songs.add(Song(
                        id          = id,
                        title       = title,
                        artist      = artist,
                        albumArtist = albumArtist,
                        album       = album,
                        year        = year,
                        trackNumber = trackNumber,
                        genre       = genre,
                        duration    = duration,
                        uri         = uri
                    ))
                } catch (e: Exception) {
                    android.util.Log.w("MusicPlayer", "Errore lettura tag: $path — ${e.message}")
                    // fallback minimo
                    songs.add(Song(
                        id          = id,
                        title       = java.io.File(path).nameWithoutExtension,
                        artist      = "Artista sconosciuto",
                        albumArtist = "",
                        album       = "Album sconosciuto",
                        year        = "",
                        trackNumber = 0,
                        genre       = "",
                        duration    = duration,
                        uri         = uri
                    ))
                }
            }
        }

        android.util.Log.d("MusicPlayer", "Canzoni totali: ${songs.size}")
        return songs
    }

    // ---- Artisti ----

    /**
     * Raggruppa i brani per primo artista (prima della virgola).
     * Ordine: alfabetico per nome artista.
     */
    fun loadAllArtists(songs: List<Song>): List<Artist> {
        return songs
            .flatMap { song ->
                song.artist
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { artistName -> Pair(artistName, song) }
            }
            .groupBy { it.first }
            .map { (name, pairs) ->
                Artist(
                    name  = name,
                    songs = pairs.map { it.second }
                        .sortedWith(compareBy({ it.album }, { it.trackNumber }))
                )
            }
            .sortedBy { it.name.sortKey() }
    }

    // ---- Album ----

    /**
     * Raggruppa i brani per album.
     * Usa albumArtist se presente, altrimenti primo artista del campo artist.
     * Ordine: alfabetico per titolo album.
     */
    fun loadAllAlbums(songs: List<Song>): List<Album> {
        return songs
            .groupBy { it.album }
            .map { (albumTitle, albumSongs) ->
                val sorted      = albumSongs.sortedBy { it.trackNumber }
                val firstSong   = sorted.first()
                val albumArtist = firstSong.albumArtist
                    .takeIf { it.isNotBlank() }
                    ?: extractFirstArtist(firstSong.artist)
                Album(
                    title  = albumTitle,
                    artist = albumArtist,
                    year   = firstSong.year,
                    songs  = sorted
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    // ---- Copertine ----

    /**
     * Overload che accetta una Song — usa la uri già contenuta nel modello.
     */
    fun getAlbumArt(context: Context, song: Song): android.graphics.Bitmap? =
        getAlbumArt(context, song.uri)

    /**
     * Overload che accetta direttamente una Uri (usato internamente e da MusicService).
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
        } catch (_: Exception) {
            null
        }
    }
}