package com.everygo.musicplayer

import android.app.Application

class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
    }
}