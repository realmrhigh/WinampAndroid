package com.example.winampinspiredmp3player

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.example.winampinspiredmp3player.ui.ViewPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.example.winampinspiredmp3player.databinding.ActivityMainBinding // Import ViewBinding class

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // Declare binding variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) // Initialize binding
        setContentView(binding.root) // Set content view to binding.root

        // val viewPager: ViewPager2 = findViewById(R.id.view_pager) // Original
        // val tabLayout: TabLayout = findViewById(R.id.tab_layout) // Original
        // Use binding to access views
        val viewPager: ViewPager2 = binding.viewPager
        val tabLayout: TabLayout = binding.tabLayout


        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Player"
                1 -> "Playlist"
                2 -> "Visualizer"
                else -> null
            }
        }.attach()
    }

    // Public method to switch tabs
    fun switchToPlayerTab() {
        binding.viewPager.currentItem = 0 // Assuming 0 is the index for PlayerFragment
    }
}
