package com.example.winampinspiredmp3player.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.winampinspiredmp3player.ui.player.PlayerFragment
import com.example.winampinspiredmp3player.ui.playlist.PlaylistFragment
import com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PlayerFragment()
            1 -> PlaylistFragment()
            2 -> VisualizerFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
