package com.example.musicplayer

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

data class Genre(
    val name: String,
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

        val genreMap = fetchGenresFromMediaStore(context)

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
                    genre       = genreMap[id] ?: "",
                    duration    = cursor.getLong(durationCol),
                    uri         = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                ))
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
            .groupBy { extractFirstArtist(it.artist) }
            .map { (name, artistSongs) ->
                Artist(
                    name  = name,
                    songs = artistSongs.sortedWith(
                        compareBy({ it.album }, { it.trackNumber })
                    )
                )
            }
            .sortedBy { it.name.lowercase() }
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

    // ---- Generi ----

    /**
     * Raggruppa i brani per genere dalla List<Song> già in memoria.
     * I brani senza genere finiscono nel gruppo "Sconosciuto".
     * Ordine: alfabetico per nome genere.
     */
    fun loadAllGenres(songs: List<Song>): List<Genre> {
        return songs
            .groupBy { it.genre.takeIf { g -> g.isNotBlank() } ?: "Sconosciuto" }
            .map { (name, genreSongs) ->
                Genre(
                    name  = name,
                    songs = genreSongs.sortedBy { it.title }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // Chiamata SOLO internamente da getAllSongs() per popolare Song.genre
    private fun fetchGenresFromMediaStore(context: Context): Map<Long, String> {
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
        } catch (e: Exception) {
            null
        }
    }
}