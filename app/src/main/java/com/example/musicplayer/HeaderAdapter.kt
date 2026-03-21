package com.example.musicplayer

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class HeaderAdapter(private val view: View) : RecyclerView.Adapter<HeaderAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(view)
    override fun onBindViewHolder(holder: VH, position: Int) {}
    override fun getItemCount() = 1
}