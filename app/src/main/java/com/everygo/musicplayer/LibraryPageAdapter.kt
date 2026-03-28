package com.everygo.musicplayer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LibraryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SongsFragment()
        1 -> ArtistsFragment()
        2 -> AlbumsFragment()
        3 -> PlaylistsFragment()
        else -> SongsFragment()
    }
}