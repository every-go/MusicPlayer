package com.everygo.musicplayer

class HistoryQueue {

    var playlist: List<Song> = emptyList()
        set(value) {
            field = value
            navigationHistory.clear()
            historyPos = -1
        }

    var isShuffleEnabled: Boolean = true
    var explicitNextCount: Int = 0

    private val navigationHistory = mutableListOf<Int>()
    private var historyPos: Int = -1

    val currentIndex: Int
        get() = navigationHistory.getOrNull(historyPos) ?: -1

    val currentSong: Song?
        get() = playlist.getOrNull(currentIndex)

    @Synchronized
    fun moveTo(index: Int): Song? {
        if (playlist.isEmpty()) return null
        val safeIndex = index.coerceIn(playlist.indices)
        truncateFuture()
        navigationHistory.add(safeIndex)
        historyPos = navigationHistory.lastIndex
        return playlist[safeIndex]
    }

    private val explicitQueue = ArrayDeque<Int>()

    fun enqueueNext(index: Int) {
        explicitQueue.addLast(index)
    }

    @Synchronized
    fun next(): Song? {
        if (playlist.isEmpty()) return null

        if (historyPos == -1) {
            navigationHistory.add(0)
            historyPos = 0
            return currentSong
        }

        // PRIORITÀ: coda esplicita
        if (explicitQueue.isNotEmpty()) {
            val nextIndex = explicitQueue.removeFirst()
            truncateFuture()
            navigationHistory.add(nextIndex)
            historyPos = navigationHistory.lastIndex
            return currentSong
        }

        if (historyPos < navigationHistory.lastIndex) {
            historyPos++
            return currentSong
        }

        val nextIndex = if (isShuffleEnabled && playlist.size > 1) {
            playlist.indices.filter { it != currentIndex }.random()
        } else {
            (currentIndex + 1).coerceAtMost(playlist.lastIndex)
        }

        navigationHistory.add(nextIndex)
        historyPos = navigationHistory.lastIndex
        return currentSong
    }

    @Synchronized
    fun previous(): PreviousAction {
        if (playlist.isEmpty() || historyPos == -1) {
            return PreviousAction.SeekToStart
        }

        return if (historyPos > 0) {
            historyPos--
            PreviousAction.PlaySong(currentSong!!)
        } else {
            PreviousAction.SeekToStart
        }
    }

    private fun truncateFuture() {
        if (historyPos < navigationHistory.lastIndex) {
            navigationHistory.subList(historyPos + 1, navigationHistory.size).clear()
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