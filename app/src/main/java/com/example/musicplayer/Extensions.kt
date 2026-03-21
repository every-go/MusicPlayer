package com.example.musicplayer
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
        "0$this" // ← precede 'a' nell'ordinamento
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

fun showSongMenu(
    context: android.content.Context,
    anchor: android.view.View,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToPlaylist: ((Playlist) -> Unit)? = null
) {
    val popup = android.widget.PopupMenu(context, anchor)
    var id = 0
    if (onPlayNext != null)      popup.menu.add(0, id++, id, "Riproduci successivo")
    if (onAddToPlaylist != null) popup.menu.add(0, id++, id, "Aggiungi a playlist")
    if (onEdit != null)          popup.menu.add(0, id++, id, "Modifica")
    if (onDelete != null)        popup.menu.add(0, id++, id, "Elimina")

    popup.setOnMenuItemClickListener { item ->
        var currentId = 0
        if (onPlayNext != null && item.itemId == currentId++) { onPlayNext(); return@setOnMenuItemClickListener true }
        if (onAddToPlaylist != null && item.itemId == currentId++) {
            val playlists = PlaylistRepository.load(context)
            val names = (listOf("+ Nuova playlist") + playlists.map { it.name }).toTypedArray()
            android.app.AlertDialog.Builder(context)
                .setTitle("Aggiungi a playlist")
                .setItems(names) { _, which ->
                    if (which == 0) {
                        val input = android.widget.EditText(context).apply {
                            hint = "Nome playlist"
                            setTextColor(0xFFFFFFFF.toInt())
                            setHintTextColor(0xFFAAAAAA.toInt())
                        }
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Nuova playlist")
                            .setView(input)
                            .setPositiveButton("Crea") { _, _ ->
                                val name = input.text.toString().trim()
                                if (name.isNotEmpty()) {
                                    val newPlaylist = PlaylistRepository.create(context, name)
                                    onAddToPlaylist(newPlaylist)
                                }
                            }
                            .setNegativeButton("Annulla", null)
                            .show()
                    } else {
                        onAddToPlaylist(playlists[which - 1])
                    }
                }
                .show()
            return@setOnMenuItemClickListener true
        }
        if (onEdit != null && item.itemId == currentId++) { onEdit(); return@setOnMenuItemClickListener true }
        if (onDelete != null && item.itemId == currentId) { onDelete(); return@setOnMenuItemClickListener true }
        false
    }
    popup.show()
}