package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat

class MusicService : Service() {

    // ---- Binder ----
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    private val binder = MusicBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ---- Player ----
    private var mediaPlayer: MediaPlayer? = null
    var playlist: List<Song> = emptyList()

    val currentSong get() = playlist.getOrNull(currentIndex)
    val isPlaying get() = mediaPlayer?.isPlaying ?: false
    val currentPosition get() = mediaPlayer?.currentPosition ?: 0
    val duration get() = mediaPlayer?.duration ?: 0

    // Callbacks verso le Activity
    val onSongChanged = mutableListOf<(Song) -> Unit>()
    val onPlaybackStateChanged = mutableListOf<(Boolean) -> Unit>()

    // ---- MediaSession ----
    // È l'oggetto che parla con Android per:
    //   - mostrare i controlli sul lock screen
    //   - mostrare i controlli nel notification shade
    //   - rispondere ai tasti fisici (cuffie, bluetooth)
    private lateinit var mediaSession: MediaSessionCompat

    // ---- Lifecycle ----

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
        super.onDestroy()
    }

    // ---- MediaSession setup ----

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayerSession")

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay()                { if (!isPlaying) togglePlayPause() }
            override fun onPause()               { if (isPlaying)  togglePlayPause() }
            override fun onSkipToNext()          { playNext() }
            override fun onSkipToPrevious()      { playPrevious() }
            override fun onSeekTo(pos: Long)     { seekTo(pos.toInt()) }
        })

        mediaSession.setPlaybackState(buildPlaybackState(false))
        mediaSession.isActive = true
    }

    // ---- Controlli pubblici ----

    private var history = mutableListOf<Int>()
    private var forwardHistory = mutableListOf<Int>()

    var currentIndex = -1

    fun playSong(index: Int, addToHistory: Boolean = true) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size) return

        if (addToHistory && currentIndex != -1 && currentIndex != index) {
            history.add(currentIndex)
            forwardHistory.clear()
        }
        currentIndex = index
        val song = playlist[index]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(applicationContext, song.uri)
            prepare()
            start()
            setOnCompletionListener { playNext() }
        }

        onSongChanged.forEach { it(song) }
        onPlaybackStateChanged.forEach { it(true) }
        updateMediaSessionPlaybackState(true)
        updateMediaSessionMetadata(song)

        // UN solo Thread per copertina, notifica, widget
        Thread {
            val art = SongRepository.getAlbumArt(this, song.uri)
            updateNotification(song, art)
            updateMediaSessionMetadata(song, art)
            MusicWidgetProvider.update(this, song, art, true)
        }.start()
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            onPlaybackStateChanged.forEach { it(false) }
            updateMediaSessionPlaybackState(false)
        } else {
            player.start()
            onPlaybackStateChanged.forEach { it(true) }
            updateMediaSessionPlaybackState(true)
        }
        currentSong?.let { song ->
            Thread {
                val art = SongRepository.getAlbumArt(this, song.uri)
                updateNotification(song, art)
            }.start()
        }
    }

    var isShuffleEnabled: Boolean = true

    fun playNext() {
        if (playlist.isEmpty()) return

        val next = if (isShuffleEnabled && playlist.size > 1) {
            if (forwardHistory.isNotEmpty()) {
                val nextIndex = forwardHistory.removeAt(forwardHistory.size - 1)
                history.add(currentIndex)
                playSong(nextIndex, addToHistory = false)
                return
            } else {
                (0 until playlist.size).filter { it != currentIndex }.random()
            }
        } else {
            (currentIndex + 1) % playlist.size
        }
        playSong(next)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
    }

    fun playPrevious() {
        if ((mediaPlayer?.currentPosition ?: 0) > 3000) {
            mediaPlayer?.seekTo(0)
        } else if (history.isNotEmpty()) {
            forwardHistory.add(currentIndex)
            val prevIndex = history.removeAt(history.size - 1)
            playSong(prevIndex, addToHistory = false)
        }
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
    }

    // ---- MediaSession aggiornamento ----

    private fun updateMediaSessionMetadata(song: Song, art: Bitmap? = null) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,        song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,       song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,        song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.albumArtist)
            .putLong(  MediaMetadataCompat.METADATA_KEY_DURATION,     song.duration)
            .putLong(  MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, song.trackNumber.toLong())
            .apply { if (art != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art) }
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updateMediaSessionPlaybackState(playing: Boolean) {
        mediaSession.setPlaybackState(buildPlaybackState(playing))
    }

    private fun buildPlaybackState(playing: Boolean): PlaybackStateCompat {
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                (mediaPlayer?.currentPosition ?: 0).toLong(),
                1f
            )
            .build()
    }

    // ---- Notifica (Foreground Service) ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(song: Song, art: Bitmap? = null): Notification {
        val openPlayerIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openPlayerIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Azioni nella notifica: precedente, play/pausa, successivo
        val prevIntent = PendingIntent.getService(this, 1,
            Intent(this, MusicService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = PendingIntent.getService(this, 2,
            Intent(this, MusicService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = PendingIntent.getService(this, 3,
            Intent(this, MusicService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setSmallIcon(R.drawable.ic_play_white_24dp)
            .setLargeIcon(art)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // visibile sul lock screen
            // Collega la notifica alla MediaSession → Android usa i metadati per il lock screen
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_skip_previous_white_24dp, "Precedente", prevIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause_white_24dp else R.drawable.ic_play_white_24dp,
                if (isPlaying) "Pausa" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next_white_24dp, "Successivo", nextIntent)
            .build()
    }

    private fun updateNotification(song: Song, art: Bitmap? = null) {
        val notification = buildNotification(song, art)
        startForeground(NOTIFICATION_ID, notification)
    }

    // Gestione Intent dalla notifica (bottoni precedente/play-pausa/successivo)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT       -> playNext()
            ACTION_PREVIOUS   -> playPrevious()
        }
        return START_STICKY
    }

    companion object {
        const val CHANNEL_ID      = "music_player_channel"
        const val NOTIFICATION_ID = 1

        // Azioni per i bottoni nella notifica
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_NEXT       = "ACTION_NEXT"
        const val ACTION_PREVIOUS   = "ACTION_PREVIOUS"
    }
}