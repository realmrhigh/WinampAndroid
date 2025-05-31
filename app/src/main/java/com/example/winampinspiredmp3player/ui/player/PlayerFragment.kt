package com.example.winampinspiredmp3player.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager // AudioManager import restored
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.winampinspiredmp3player.MainActivity
import com.example.winampinspiredmp3player.R // Import R class for drawable resources
import com.example.winampinspiredmp3player.databinding.FragmentPlayerBinding
import com.example.winampinspiredmp3player.services.MusicService
import com.example.winampinspiredmp3player.data.Track

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var audioManager: AudioManager // AudioManager instance restored
    private var musicService: MusicService? = null
    private var isBound: Boolean = false

    // SharedPreferences constants removed, will use VisualizerFragment's constants
    // private companion object {
    //     private const val VISUALIZER_PREFS_NAME = "VisualizerPrefs"
    //     private const val KEY_VISUALIZER_ENABLED = "isVisualizerManuallyEnabled"
    // }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("PlayerFragment", "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            // Volume diagnostic code restored
            Log.d("PlayerFragment", "onServiceConnected: Diagnosing audio volume.")
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Log.d("PlayerFragment", "Max volume: $maxVolume, Current volume: $currentVolume")

            if (maxVolume > 0) { // Ensure maxVolume is positive before division and setting
                val targetVolume = maxVolume / 2
                if (currentVolume == 0) {
                    Log.d("PlayerFragment", "Current volume is 0. Setting to 50% ($targetVolume) for diagnosis.")
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                    val newCurrentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    Log.d("PlayerFragment", "Volume after setting: $newCurrentVolume")
                } else {
                    Log.d("PlayerFragment", "Current volume is not 0. No diagnostic change made to volume by PlayerFragment.")
                }
            } else {
                Log.d("PlayerFragment", "Max volume is 0, cannot set volume.")
            }

            updatePlayPauseButtonState()
            observeLiveData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("PlayerFragment", "Service Disconnected")
            musicService?.playbackPosition?.removeObservers(viewLifecycleOwner)
            musicService?.currentTrackDuration?.removeObservers(viewLifecycleOwner)
            musicService?.currentPlayingTrack?.removeObservers(viewLifecycleOwner)
            musicService = null
            isBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager // AudioManager initialization restored
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        setupTrackProgressSeekBar()
    }

    private fun setupClickListeners() {
        binding.btnPlayPause.setOnClickListener {
            if (isBound && musicService != null) {
                if (musicService!!.isPlaying()) {
                    musicService!!.pauseTrack()
                } else {
                    if (musicService!!.currentTrack != null) {
                        musicService!!.playTrackAtIndex(musicService!!.currentTrackIndex)
                    } else {
                        Log.d("PlayerFragment", "Play clicked, but no track is loaded/selected.")
                        // Optionally play first track from list if available
                        // musicService!!.playTrackAtIndex(0)
                    }
                }
                updatePlayPauseButtonState() // Update image after action
            } else {
                Log.d("PlayerFragment", "Play/Pause button clicked, but service not bound.")
            }
        }

        // binding.btnStopTrack.setOnClickListener removed

        binding.btnToggleShufflePlayer.setOnClickListener {
            if (isBound && musicService != null) {
                musicService!!.toggleShuffleMode()
                val shuffleEnabled = musicService!!.isShuffleEnabled()
                binding.btnToggleShufflePlayer.alpha = if (shuffleEnabled) 1.0f else 0.5f
                Toast.makeText(requireContext(), if (shuffleEnabled) "Shuffle On" else "Shuffle Off", Toast.LENGTH_SHORT).show()
                Log.d("PlayerFragment", "Toggle Shuffle clicked. Shuffle is now: $shuffleEnabled")
            } else {
                Log.d("PlayerFragment", "Toggle Shuffle clicked, but service not bound.")
            }
        }

        binding.btnPreviousTrack.setOnClickListener {
            if (isBound && musicService != null) {
                musicService!!.playPreviousTrack()
            } else {
                Log.d("PlayerFragment", "Previous Track Clicked, but service not bound.")
            }
        }
        binding.btnNextTrack.setOnClickListener {
            if (isBound && musicService != null) {
                musicService!!.playNextTrack()
            } else {
                Log.d("PlayerFragment", "Next Track Clicked, but service not bound.")
            }
        }

        binding.btnToggleVisualizer.setOnClickListener {
            Log.d("PlayerFragment", "btn_toggle_visualizer clicked. Calling MainActivity.onUserToggledVisualizerPreference().")
            (activity as? MainActivity)?.onUserToggledVisualizerPreference()
            // Immediate button update might be slightly delayed if relying solely on onResume.
            // MainActivity will call back to updateVisualizerButtonFromMain for more immediate feedback.
        }
    }

    fun updateVisualizerButtonFromMain(isEnabled: Boolean) { // New public method
        Log.d("PlayerFragment", "updateVisualizerButtonFromMain called by MainActivity. New state: $isEnabled. Updating button icon.")
        updateVisualizerToggleButtonState(isEnabled)
    }

    private fun updateVisualizerToggleButtonState(isEnabled: Boolean) {
        if (isEnabled) {
            // Assuming ic_menu_view is "on" state or use a specific "eye on" icon
            binding.btnToggleVisualizer.setImageResource(android.R.drawable.ic_menu_view)
            // Or use alpha: binding.btnToggleVisualizer.imageAlpha = 255
        } else {
            // Use a different icon for "off" state, e.g., "eye off" or change tint/alpha
            binding.btnToggleVisualizer.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Placeholder for "off"
            // Or use alpha: binding.btnToggleVisualizer.imageAlpha = 128
        }
    }

    private fun setupTrackProgressSeekBar() {
        binding.sbTrackProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Optionally update a TextView with current progress / duration
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    if (isBound && musicService != null) {
                        musicService!!.seekTo(it.progress)
                    }
                }
            }
        })
    }

    private fun updatePlayPauseButtonState() {
        if (isBound && musicService != null) {
            if (musicService!!.isPlaying()) {
                binding.btnPlayPause.setImageResource(R.drawable.winamp_btn_pause)
            } else {
                binding.btnPlayPause.setImageResource(R.drawable.winamp_btn_play)
            }
        } else {
            binding.btnPlayPause.setImageResource(R.drawable.winamp_btn_play) // Default state
        }
    }

    private fun observeLiveData() {
        musicService?.playbackPosition?.observe(viewLifecycleOwner) { position ->
            binding.sbTrackProgress.progress = position
        }
        musicService?.currentTrackDuration?.observe(viewLifecycleOwner) { duration ->
            binding.sbTrackProgress.max = duration
        }
        musicService?.currentPlayingTrack?.observe(viewLifecycleOwner) { track ->
            updateTrackInfoUI(track)
            updatePlayPauseButtonState() // Update play/pause button when track changes or stops
        }
    }

    private fun updateTrackInfoUI(track: Track?) {
        if (track != null) {
            val titleToDisplay = if (track.title.isNullOrBlank()) track.fileName else track.title
            val artistToDisplay = if (track.artist.isNullOrBlank()) "<Unknown Artist>" else track.artist
            binding.tvCurrentTrackInfo.text = "$titleToDisplay - $artistToDisplay"
            // Duration is handled by sb_track_progress.max and sb_track_progress.progress via LiveData
        } else {
            binding.tvCurrentTrackInfo.text = "No track playing"
            binding.sbTrackProgress.progress = 0
            binding.sbTrackProgress.max = 100 // Default max when no track
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("PlayerFragment", "onStart called, binding to service.")
        Intent(requireActivity(), MusicService::class.java).also { intent ->
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button state when fragment resumes, in case it was changed by other means or for initial setup
        val prefs = requireActivity().getSharedPreferences(com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment.VISUALIZER_PREFS_NAME, Context.MODE_PRIVATE)
        val visualizerIsEnabled = prefs.getBoolean(com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment.KEY_VISUALIZER_ENABLED, false) // Default changed to false
        Log.d("PlayerFragment", "onResume: Visualizer state from Prefs: $visualizerIsEnabled. Updating button icon.")
        updateVisualizerToggleButtonState(visualizerIsEnabled)

        // Update shuffle button state on resume
        if (isBound && musicService != null) {
            val shuffleEnabled = musicService!!.isShuffleEnabled()
            binding.btnToggleShufflePlayer.alpha = if (shuffleEnabled) 1.0f else 0.5f
            Log.d("PlayerFragment", "onResume: Shuffle state from service: $shuffleEnabled. Updated button alpha.")
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("PlayerFragment", "onStop called, unbinding from service.")
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound && musicService != null) {
            musicService?.playbackPosition?.removeObservers(viewLifecycleOwner)
            musicService?.currentTrackDuration?.removeObservers(viewLifecycleOwner)
            musicService?.currentPlayingTrack?.removeObservers(viewLifecycleOwner)
        }
        _binding = null
        Log.d("PlayerFragment", "onDestroyView called, _binding set to null")
    }
}
