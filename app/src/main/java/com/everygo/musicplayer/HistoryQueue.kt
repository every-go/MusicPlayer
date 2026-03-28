package com.everygo.musicplayer

class HistoryQueue {

    var playlist: List<Song> = emptyList()
    var currentIndex: Int = -1
    var isShuffleEnabled: Boolean = true

    var hasExplicitNext = false

    private val history        = mutableListOf<Int>()
    private val forwardHistory = mutableListOf<Int>()

    val currentSong get() = playlist.getOrNull(currentIndex)

    /**
     * Registra l'indice corrente nello storico e aggiorna currentIndex.
     * Ritorna la Song da riprodurre, o null se l'indice non è valido.
     */
    @Synchronized
    fun moveTo(index: Int, addToHistory: Boolean = true): Song? {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size) return null

        if (addToHistory && currentIndex != -1 && currentIndex != index) {
            history.add(currentIndex)
            forwardHistory.clear()
        }
        currentIndex = index
        return playlist[index]
    }

    /**
     * Calcola il prossimo indice (shuffle o lineare), aggiorna lo stato
     * e ritorna la Song successiva. Ritorna null se la playlist è vuota.
     */
    @Synchronized
    fun next(): Song? {
        if (playlist.isEmpty()) return null

        if (hasExplicitNext) {
            hasExplicitNext = false
            return moveTo(currentIndex + 1)
        }

        if (isShuffleEnabled && playlist.size > 1) {
            if (forwardHistory.isNotEmpty()) {
                val nextIndex = forwardHistory.removeAt(forwardHistory.size - 1)
                history.add(currentIndex)
                currentIndex = nextIndex
                return playlist[currentIndex]
            }
            val nextIndex = (0 until playlist.size).filter { it != currentIndex }.random()
            return moveTo(nextIndex)
        }

        return moveTo((currentIndex + 1) % playlist.size)
    }

    /**
     * Determina l'azione da eseguire quando l'utente preme "precedente":
     *   - PlaySong  → naviga alla canzone precedente nello storico
     *   - SeekToStart → non c'è storia, si deve tornare all'inizio del brano corrente
     *
     * Nota: la decisione basata sulla posizione corrente (> 3 secondi) rimane
     * in MusicService, perché dipende da MediaPlayer.
     */
    @Synchronized
    fun previous(): PreviousAction {
        return if (history.isNotEmpty()) {
            forwardHistory.add(currentIndex)
            val prevIndex = history.removeAt(history.size - 1)
            currentIndex = prevIndex
            PreviousAction.PlaySong(playlist[prevIndex])
        } else {
            PreviousAction.SeekToStart
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    /** Risultato possibile di previous() */
    sealed class PreviousAction {
        data class PlaySong(val song: Song) : PreviousAction()
        object SeekToStart : PreviousAction()
    }
}