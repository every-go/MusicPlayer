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