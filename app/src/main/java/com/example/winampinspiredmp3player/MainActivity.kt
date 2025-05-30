package com.example.winampinspiredmp3player

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.example.winampinspiredmp3player.databinding.ActivityMainBinding // Import ViewBinding class
import com.example.winampinspiredmp3player.ui.player.PlayerFragment // Import PlayerFragment
import com.example.winampinspiredmp3player.ui.playlist.PlaylistFragment // Import PlaylistFragment
import com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment // Import VisualizerFragment

class MainActivity : AppCompatActivity(), VisualizerFragment.VisualizerVisibilityListener {

    private lateinit var binding: ActivityMainBinding // Declare binding variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) // Initialize binding
        setContentView(binding.root) // Set content view to binding.root

        if (savedInstanceState == null) { // Important to avoid adding fragments on config change
            supportFragmentManager.beginTransaction()
                .replace(R.id.player_controls_container, PlayerFragment())
                .replace(R.id.playlist_container, PlaylistFragment())
                .replace(R.id.visualizer_container, VisualizerFragment())
                .commitNow() // or commit()
        }
    }

    override fun setVisualizerContainerVisible(isVisible: Boolean) {
        Log.d("MainActivity", "Setting visualizer container visibility: ${if (isVisible) "VISIBLE" else "GONE"}")
        binding.visualizerContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
}
