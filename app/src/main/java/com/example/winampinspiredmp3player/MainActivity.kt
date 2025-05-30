package com.example.winampinspiredmp3player

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.example.winampinspiredmp3player.databinding.ActivityMainBinding // Import ViewBinding class
import com.example.winampinspiredmp3player.ui.player.PlayerFragment // Import PlayerFragment
import com.example.winampinspiredmp3player.ui.playlist.PlaylistFragment // Import PlaylistFragment
import com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment // Import VisualizerFragment
import android.content.Context // For SharedPreferences

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
        Log.d("MainActivity", "setVisualizerContainerVisible: Setting visualizer_container visibility to ${if (isVisible) "VISIBLE" else "GONE"}. Current visibility: ${binding.visualizerContainer.visibility}");
        binding.visualizerContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    fun onUserToggledVisualizerPreference() {
        val prefs = getSharedPreferences(VisualizerFragment.VISUALIZER_PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = prefs.getBoolean(VisualizerFragment.KEY_VISUALIZER_ENABLED, true) // Default true
        Log.d("MainActivity", "onUserToggledVisualizerPreference: Current state from Prefs: $currentState");
        val newState = !currentState
        Log.d("MainActivity", "onUserToggledVisualizerPreference: New state to save: $newState");
        prefs.edit().putBoolean(VisualizerFragment.KEY_VISUALIZER_ENABLED, newState).apply()
        Log.d("MainActivity", "onUserToggledVisualizerPreference: Saved new state to Prefs.");

        // Update MainActivity's container for the visualizer
        Log.d("MainActivity", "onUserToggledVisualizerPreference: Calling setVisualizerContainerVisible($newState).");
        setVisualizerContainerVisible(newState) // This method already exists

        // Notify PlayerFragment to update its button icon
        Log.d("MainActivity", "onUserToggledVisualizerPreference: Calling playerFragment.updateVisualizerButtonFromMain($newState).");
        val playerFragment = supportFragmentManager.findFragmentById(R.id.player_controls_container) as? PlayerFragment
        playerFragment?.updateVisualizerButtonFromMain(newState)

        // Notify VisualizerFragment to refresh its internal state and view
        Log.d("MainActivity", "onUserToggledVisualizerPreference: Calling visualizerFragment.refreshStateFromPreferences().");
        val visualizerFragment = supportFragmentManager.findFragmentById(R.id.visualizer_container) as? VisualizerFragment
        visualizerFragment?.refreshStateFromPreferences()
    }
}
