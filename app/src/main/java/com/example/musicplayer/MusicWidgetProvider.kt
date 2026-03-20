package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * Widget sulla home screen.
 *
 * I widget NON possono accedere alle View normalmente —
 * usano RemoteViews, un sistema speciale che funziona
 * anche nel processo del launcher di Android.
 *
 * Per aggiornare il widget dal Service chiamare:
 *   MusicWidgetProvider.update(context, song, albumArt, isPlaying)
 */
class MusicWidgetProvider : AppWidgetProvider() {

    // Chiamato quando il widget viene aggiunto alla home o aggiornato
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId, null, null, false)
        }
    }

    companion object {

        /**
         * Chiamato da MusicService ogni volta che cambia la canzone o lo stato.
         * Aggiorna TUTTI i widget presenti sulla home screen.
         */
        fun update(
            context: Context,
            song: Song?,
            albumArt: Bitmap?,
            isPlaying: Boolean
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id, song, albumArt, isPlaying)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            song: Song?,
            albumArt: Bitmap?,
            isPlaying: Boolean
        ) {
            // RemoteViews: l'unico modo per modificare le View di un widget
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            // ---- Testo ----
            views.setTextViewText(R.id.widgetTitle,  song?.title  ?: "Nessun brano")
            views.setTextViewText(R.id.widgetArtist, song?.artist ?: "")

            // ---- Copertina ----
            if (albumArt != null) {
                views.setImageViewBitmap(R.id.widgetAlbumArt, albumArt)
            } else {
                views.setImageViewResource(R.id.widgetAlbumArt, R.drawable.ic_launcher_foreground)
            }

            // ---- Icona play/pausa ----
            views.setImageViewResource(
                R.id.widgetPlayPause,
                if (isPlaying) R.drawable.ic_pause_white_24dp
                else R.drawable.ic_play_white_24dp
            )

            // ---- PendingIntent per ogni bottone ----
            // I widget usano broadcast Intent per comunicare con il Service
            views.setOnClickPendingIntent(
                R.id.widgetPlayPause,
                buildServiceIntent(context, MusicService.ACTION_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widgetNext,
                buildServiceIntent(context, MusicService.ACTION_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.widgetPrevious,
                buildServiceIntent(context, MusicService.ACTION_PREVIOUS)
            )

            // Click sulla barra → apri MainActivity
            val openAppIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, PlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetTitle,  openAppIntent)
            views.setOnClickPendingIntent(R.id.widgetArtist, openAppIntent)

            // Applica le modifiche al widget
            manager.updateAppWidget(widgetId, views)
        }

        // Crea un PendingIntent che avvia il Service con una certa azione
        private fun buildServiceIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                action.hashCode(),   // requestCode univoco per ogni azione
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
