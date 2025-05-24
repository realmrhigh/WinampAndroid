package com.example.winampinspiredmp3player.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.example.winampinspiredmp3player.databinding.FragmentPlayerBinding
import com.example.winampinspiredmp3player.services.MusicService
import com.example.winampinspiredmp3player.data.Track // Ensure Track is imported

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var musicService: MusicService? = null
    private var isBound: Boolean = false
    private lateinit var audioManager: AudioManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("PlayerFragment", "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            setupVolumeControls() // Initial volume setup
            updatePlayPauseButtonState() // Update button based on service state
            observeLiveData() // Start observing LiveData from service
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
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        setupTrackProgressSeekBar()
        setupVolumeControls() // Initial setup, might be re-applied if service connects
    }

    private fun setupClickListeners() {
        binding.btnPlayPause.setOnClickListener {
            if (isBound && musicService != null) {
                if (musicService!!.isPlaying()) {
                    musicService!!.pauseTrack()
                } else {
                    // If there's a current track (e.g., paused), resume it
                    // Otherwise, this button won't start a new track without selection from playlist
                    if (musicService!!.currentTrack != null) {
                         // The service's playTrackAtIndex or playTrack (if URI is known)
                         // should handle resuming or starting.
                         // For now, assume if currentTrack is not null, it can be played/resumed.
                         // A dedicated resume function in service might be better.
                         // For simplicity, if a track is loaded (currentTrack != null) and not playing,
                         // play it. This assumes playTrackAtIndex can handle resuming.
                         musicService!!.playTrackAtIndex(musicService!!.currentTrackIndex)
                    } else {
                        Log.d("PlayerFragment", "Play clicked, but no track is loaded/selected.")
                    }
                }
                updatePlayPauseButtonState()
            } else {
                Log.d("PlayerFragment", "Play/Pause button clicked, but service not bound.")
            }
        }

        binding.btnStopTrack.setOnClickListener {
            if (isBound && musicService != null) {
                musicService!!.stopTrack()
                // UI updates will be handled by LiveData observers mostly
            } else {
                Log.d("PlayerFragment", "Stop button clicked, but service not bound.")
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


    private fun setupVolumeControls() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        binding.sbVolumeControl.max = maxVolume
        binding.sbVolumeControl.progress = currentVolume

        binding.sbVolumeControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePlayPauseButtonState() {
        if (isBound && musicService != null) {
            if (musicService!!.isPlaying()) {
                binding.btnPlayPause.text = "Pause"
            } else {
                binding.btnPlayPause.text = "Play"
            }
        } else {
            binding.btnPlayPause.text = "Play" // Default state when not bound
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
            updatePlayPauseButtonState() // Also update button when track changes
        }
    }

    private fun updateTrackInfoUI(track: Track?) {
        if (track != null) {
            binding.tvCurrentTrackInfo.text = track.title ?: track.fileName
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
            // Start the service if it's not already running, so it can run in the background
            // requireActivity().startService(intent) // This line is important for long-running playback
            // For now, BIND_AUTO_CREATE will create it, but it will be destroyed if all clients unbind
            // and it wasn't started. For a music player, startService is usually desired.
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("PlayerFragment", "onStop called, unbinding from service.")
        if (isBound) {
            // Important: Remove observers to prevent issues when fragment view is destroyed
            // but fragment instance might still be around (e.g. back stack)
            // This is now handled in onServiceDisconnected for more robustness
            // musicService?.playbackPosition?.removeObservers(viewLifecycleOwner)
            // musicService?.currentTrackDuration?.removeObservers(viewLifecycleOwner)
            // musicService?.currentPlayingTrack?.removeObservers(viewLifecycleOwner)

            requireActivity().unbindService(serviceConnection)
            isBound = false
            // musicService = null // Nullified in onServiceDisconnected
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("PlayerFragment", "onDestroyView called, _binding set to null")
        // If service is not null and still bound, good to remove observers here too,
        // though onStop should ideally handle unbinding.
        // This check is more of a safeguard.
        if (isBound && musicService != null) {
             musicService?.playbackPosition?.removeObservers(viewLifecycleOwner)
             musicService?.currentTrackDuration?.removeObservers(viewLifecycleOwner)
             musicService?.currentPlayingTrack?.removeObservers(viewLifecycleOwner)
        }
        _binding = null
    }
}
