package com.example.winampinspiredmp3player.ui.visualizer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.example.winampinspiredmp3player.R
import com.example.winampinspiredmp3player.databinding.FragmentVisualizerBinding
import com.example.winampinspiredmp3player.services.MusicService

class VisualizerFragment : Fragment() {

    private var _binding: FragmentVisualizerBinding? = null
    private val binding get() = _binding!!

    // Service related variables
    private var musicService: MusicService? = null
    private var isBound: Boolean = false
    private var videoIsPrepared: Boolean = false
    private var isVisualizerManuallyEnabled: Boolean = true // New state variable

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("VisualizerFragment", "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            musicService?.isPlayingState?.observe(viewLifecycleOwner) { isPlaying ->
                Log.d("VisualizerFragment", "Music isPlayingState changed: $isPlaying")
                updateVisualizerState() // Call the central logic
            }
            updateVisualizerState() // Initial update after service connection
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("VisualizerFragment", "Service Disconnected")
            musicService?.isPlayingState?.removeObservers(viewLifecycleOwner)
            musicService = null
            isBound = false
            // Ensure video is paused if service disconnects
            if (binding.videoViewVisualizer.isPlaying) {
                binding.videoViewVisualizer.pause()
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVisualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("VisualizerFragment", "onViewCreated")
        val videoUri =
            ("android.resource://" + requireActivity().packageName + "/" + R.raw.visualization_loop).toUri()
        binding.videoViewVisualizer.setVideoURI(videoUri)

        binding.videoViewVisualizer.setOnPreparedListener { mediaPlayer ->
            Log.d("VisualizerFragment", "Video prepared.")
            videoIsPrepared = true
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f) // Mute the video
            updateVisualizerState() // Call the central logic
        }

        binding.videoViewVisualizer.setOnErrorListener { _, what, extra ->
            Log.e("VisualizerFragment", "VideoView Error: what: $what, extra: $extra")
            videoIsPrepared = false
            true
        }

        binding.videoViewVisualizer.setOnClickListener {
            isVisualizerManuallyEnabled = !isVisualizerManuallyEnabled
            updateVisualizerState()
            Toast.makeText(requireContext(), "Visualizer ${if (isVisualizerManuallyEnabled) "On" else "Off"}", Toast.LENGTH_SHORT).show()
            Log.d("VisualizerFragment", "Visualizer toggled: ${if (isVisualizerManuallyEnabled) "On" else "Off"}")
        }
    }

    private fun updateVisualizerState() {
        if (!isAdded || _binding == null) { // Check if fragment is added and binding is not null
            Log.d("VisualizerFragment", "updateVisualizerState: Fragment not added or binding is null. Ensuring video is paused if prepared.")
             // Ensure video is paused if it was playing and fragment/binding becomes invalid
            if (_binding != null && videoIsPrepared && binding.videoViewVisualizer.isPlaying) {
                 binding.videoViewVisualizer.pause()
            }
            return
        }
        
        if (!isBound || musicService == null) {
            Log.d("VisualizerFragment", "updateVisualizerState: Service not bound or null. Ensuring video is paused if prepared.")
            if (videoIsPrepared && binding.videoViewVisualizer.isPlaying) {
                 binding.videoViewVisualizer.pause()
            }
            return
        }

        val isMusicPlaying = musicService?.isPlayingState?.value == true
        val shouldAnimate = isMusicPlaying && isVisualizerManuallyEnabled && videoIsPrepared

        if (shouldAnimate) {
            if (!binding.videoViewVisualizer.isPlaying) {
                binding.videoViewVisualizer.start()
            }
        } else {
            if (binding.videoViewVisualizer.isPlaying) {
                binding.videoViewVisualizer.pause()
            }
        }
        Log.d("VisualizerFragment", "updateVisualizerState: MusicPlaying=$isMusicPlaying, ManualEnable=$isVisualizerManuallyEnabled, Prepared=$videoIsPrepared, ShouldAnimate=$shouldAnimate, VideoViewPlaying=${if (_binding != null) binding.videoViewVisualizer.isPlaying else "N/A"}")
    }

    override fun onStart() {
        super.onStart()
        Log.d("VisualizerFragment", "onStart called, binding to service.")
        Intent(requireActivity(), MusicService::class.java).also { intent ->
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d("VisualizerFragment", "onResume called.")
        updateVisualizerState()
    }

    override fun onPause() {
        super.onPause()
        Log.d("VisualizerFragment", "onPause called.")
        // It's generally good practice to pause video when fragment is not visible.
        // The LiveData observer will also pause it if music stops.
        if (binding.videoViewVisualizer.isPlaying) {
            binding.videoViewVisualizer.pause()
            Log.d("VisualizerFragment", "Video paused in onPause.")
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("VisualizerFragment", "onStop called, unbinding from service.")
        if (isBound) {
            musicService?.isPlayingState?.removeObservers(viewLifecycleOwner) // Clean up observer
            requireActivity().unbindService(serviceConnection)
            isBound = false
            musicService = null
        }
        // Ensure video is paused when fragment stops and potentially service is unbound
        if (binding.videoViewVisualizer.isPlaying) {
            binding.videoViewVisualizer.pause()
            Log.d("VisualizerFragment", "Video paused in onStop as a fallback.")
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("VisualizerFragment", "onDestroyView called.")
        if (binding.videoViewVisualizer.isPlaying) {
            binding.videoViewVisualizer.stopPlayback()
        }
        videoIsPrepared = false
        _binding = null
        Log.d("VisualizerFragment", "VideoView playback stopped, _binding set to null")
    }
}
