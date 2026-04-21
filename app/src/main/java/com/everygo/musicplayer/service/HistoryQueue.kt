package com.everygo.musicplayer

class HistoryQueue {

    var playlist: List<Song> = emptyList()
    var currentIndex: Int = -1
    var isShuffleEnabled: Boolean = true

    var explicitNextCount: Int = 0

    private val history = mutableListOf<Int>()
    private val forwardHistory = mutableListOf<Int>()

    val currentSong: Song?
        get() = playlist.getOrNull(currentIndex)

    private fun ensureValidIndex() {
        if (playlist.isNotEmpty() && currentIndex !in playlist.indices) {
            currentIndex = 0
        }
    }

    @Synchronized
    fun moveTo(index: Int, addToHistory: Boolean = true): Song? {
        if (playlist.isEmpty()) return null

        val safeIndex = index.coerceIn(playlist.indices)

        if (addToHistory && currentIndex in playlist.indices && currentIndex != safeIndex) {
            history.add(currentIndex)
            forwardHistory.clear()
        }

        currentIndex = safeIndex
        return playlist[safeIndex]
    }

    @Synchronized
    fun next(): Song? {
        if (playlist.isEmpty()) return null

        ensureValidIndex()

        if (explicitNextCount > 0) {
            explicitNextCount--
            return moveTo(getNextSequentialIndex())
        }

        if (isShuffleEnabled && playlist.size > 1) {

            if (forwardHistory.isNotEmpty()) {
                val nextIndex = forwardHistory.removeAt(forwardHistory.lastIndex)
                return moveTo(nextIndex, addToHistory = false)
            }

            val candidates = playlist.indices.filter { it != currentIndex }
            val nextIndex = candidates.random()

            return moveTo(nextIndex)
        }

        return moveTo((currentIndex + 1).coerceAtMost(playlist.lastIndex))
    }

    @Synchronized
    fun previous(): PreviousAction {
        ensureValidIndex()

        return if (history.isNotEmpty()) {
            forwardHistory.add(currentIndex)
            val prevIndex = history.removeAt(history.lastIndex)
            currentIndex = prevIndex
            PreviousAction.PlaySong(playlist[prevIndex])
        } else {
            PreviousAction.SeekToStart
        }
    }

    private fun getNextSequentialIndex(): Int {
        ensureValidIndex()
        if (playlist.isEmpty()) return 0
        return (currentIndex + 1).coerceAtMost(playlist.lastIndex)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    sealed class PreviousAction {
        data class PlaySong(val song: Song) : PreviousAction()
        object SeekToStart : PreviousAction()
    }
}