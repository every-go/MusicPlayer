package com.everygo.musicplayer
import java.text.Normalizer

fun String.normalize(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()
}

fun Char.normalize(): Char {
    return this.toString().let {
        Normalizer.normalize(it, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }.firstOrNull() ?: this
}

fun String.sortKey(): String {
    val first = this.firstOrNull()?.normalize()
    return if (first == null || !first.isLetter()) {
        "0$this"
    } else {
        "1${this.normalize()}"
    }
}

fun String?.fixEncoding(): String? {
    if (this == null) return null
    return try {
        val converted = String(this.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        if (converted.contains('\uFFFD')) this else converted
    } catch (_: Exception) {
        this
    }
}

// IDs fissi per le voci del menu — non dipendono dall'ordine di aggiunta
private const val MENU_ID_PLAY_NEXT       = 0
private const val MENU_ID_ADD_PLAYLIST    = 1
private const val MENU_ID_GO_TO_ARTIST_SINGLE = 2
private const val MENU_ID_GO_TO_ARTIST_SUB    = 20
private const val MENU_ID_GO_TO_ALBUM     = 3
private const val MENU_ID_EDIT            = 4
private const val MENU_ID_DELETE          = 5
// sottomenu artisti: 100, 101, 102 …

fun showSongMenu(
    context: android.content.Context,
    anchor: android.view.View,
    song: Song,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null
) {
    val artists = song.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val popup = android.widget.PopupMenu(context, anchor)
    var order = 0

    if (onPlayNext != null)
        popup.menu.add(0, MENU_ID_PLAY_NEXT, order++, "Riproduci successivo")

    if (onAddToPlaylist != null)
        popup.menu.add(0, MENU_ID_ADD_PLAYLIST, order++, "Aggiungi a playlist")

    if (onGoToArtist != null) {
        if (artists.size == 1) {
            popup.menu.add(0, MENU_ID_GO_TO_ARTIST_SINGLE, order++, "Vai all'artista")
        } else if (artists.isNotEmpty()) {
            val sub = popup.menu.addSubMenu(0, MENU_ID_GO_TO_ARTIST_SUB, order++, "Vai all'artista")
            artists.forEachIndexed { i, artist ->
                sub.add(0, 100 + i, i, artist)
            }
        }
    }

    if (onGoToAlbum != null)
        popup.menu.add(0, MENU_ID_GO_TO_ALBUM, order++, "Vai all'album")

    if (onEdit != null)
        popup.menu.add(0, MENU_ID_EDIT, order++, "Modifica")

    if (onDelete != null)
        popup.menu.add(0, MENU_ID_DELETE, order++, "Elimina")

    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            MENU_ID_PLAY_NEXT -> {
                onPlayNext?.invoke()
                true
            }
            MENU_ID_ADD_PLAYLIST -> {
                val playlists = PlaylistRepository.load(context)
                val names = (listOf("+ Nuova playlist") + playlists.map { it.name }).toTypedArray()
                android.app.AlertDialog.Builder(context)
                    .setTitle("Aggiungi a playlist")
                    .setItems(names) { _, which ->
                        if (which == 0) {
                            showNewPlaylistDialog(context) { newPlaylist ->
                                onAddToPlaylist?.invoke(newPlaylist)
                            }
                        } else {
                            onAddToPlaylist?.invoke(playlists[which - 1])
                        }
                    }
                    .show()
                true
            }
            MENU_ID_GO_TO_ARTIST_SINGLE -> {
                onGoToArtist?.invoke(artists.first())
                true
            }
            in 100..199 -> {
                onGoToArtist?.invoke(artists[item.itemId - 100])
                true
            }
            MENU_ID_GO_TO_ALBUM -> {
                onGoToAlbum?.invoke()
                true
            }
            MENU_ID_EDIT -> {
                onEdit?.invoke()
                true
            }
            MENU_ID_DELETE -> {
                onDelete?.invoke()
                true
            }
            else -> false
        }
    }
    popup.show()
}

/** Dialog riutilizzabile per creare una nuova playlist con testo bianco su sfondo scuro. */
fun showNewPlaylistDialog(
    context: android.content.Context,
    onCreate: (Playlist) -> Unit
) {
    val input = android.widget.EditText(context).apply {
        hint = "Nome playlist"
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(0xFFAAAAAA.toInt())
        setBackgroundColor(0xFF2D2D2D.toInt())
        val p = (8 * context.resources.displayMetrics.density + 0.5f).toInt()
        setPadding(p, p, p, p)
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("Nuova playlist")
        .setView(input)
        .setPositiveButton("Crea") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                val newPlaylist = PlaylistRepository.create(context, name)
                onCreate(newPlaylist)
            }
        }
        .setNegativeButton("Annulla", null)
        .show()
}
