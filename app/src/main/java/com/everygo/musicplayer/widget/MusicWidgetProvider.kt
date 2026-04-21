package com.everygo.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId, null, null, false, -1)
        }
    }

    companion object {

        @JvmStatic
        fun update(
            context: Context,
            song: Song?,
            albumArt: Bitmap?,
            isPlaying: Boolean,
            currentIndex: Int = -1
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java)
            )

            for (id in ids) {
                updateWidget(context, manager, id, song, albumArt, isPlaying, currentIndex)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            song: Song?,
            albumArt: Bitmap?,
            isPlaying: Boolean,
            currentIndex: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            views.setTextViewText(R.id.widgetTitle, song?.title ?: "Nessun brano")
            views.setTextViewText(R.id.widgetArtist, song?.artist ?: "")

            if (albumArt != null) {
                views.setImageViewBitmap(R.id.widgetAlbumArt, albumArt)
            } else {
                views.setImageViewResource(
                    R.id.widgetAlbumArt,
                    R.drawable.ic_launcher_foreground
                )
            }

            views.setImageViewResource(
                R.id.widgetPlayPause,
                if (isPlaying) R.drawable.ic_pause_white_24dp
                else R.drawable.ic_play_white_24dp
            )

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

            views.setOnClickPendingIntent(
                R.id.widgetRandom,
                buildServiceIntent(context, MusicService.ACTION_RANDOM)
            )

            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, PlayerActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_SONG_INDEX", currentIndex)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            views.setOnClickPendingIntent(R.id.widgetTitle, openAppIntent)
            views.setOnClickPendingIntent(R.id.widgetArtist, openAppIntent)

            manager.updateAppWidget(widgetId, views)
        }

        private fun buildServiceIntent(
            context: Context,
            action: String
        ): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }

            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}