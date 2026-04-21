package com.everygo.musicplayer

class HistoryQueue {

    var playlist: List<Song> = emptyList()
    var currentIndex: Int = -1
    var isShuffleEnabled: Boolean = true

    /** Conta quante canzoni sono state messe in coda esplicita ("riproduci dopo").
     *  Ogni chiamata a next() decrementa il contatore e riproduce currentIndex+1
     *  in ordine, senza passare per la logica shuffle. */
    var explicitNextCount: Int = 0

    private val history        = mutableListOf<Int>()
    private val forwardHistory = mutableListOf<Int>()

    val currentSong get() = playlist.getOrNull(currentIndex)

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

    @Synchronized
    fun next(): Song? {
        if (playlist.isEmpty()) return null

        // Se ci sono canzoni messe in coda esplicita, riproducile in ordine
        if (explicitNextCount > 0) {
            explicitNextCount--
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

    sealed class PreviousAction {
        data class PlaySong(val song: Song) : PreviousAction()
        object SeekToStart : PreviousAction()
    }
}
