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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("VisualizerFragment", "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true

            musicService?.isPlayingState?.observe(viewLifecycleOwner) { isPlaying ->
                Log.d("VisualizerFragment", "Music isPlayingState changed: $isPlaying, videoIsPrepared: $videoIsPrepared")
                if (videoIsPrepared) {
                    if (isPlaying) {
                        binding.videoViewVisualizer.start()
                    } else {
                        binding.videoViewVisualizer.pause()
                    }
                }
            }

            // Immediately update video state based on current music state
            if (videoIsPrepared) {
                if (musicService?.isPlayingState?.value == true) {
                    binding.videoViewVisualizer.start()
                } else {
                    binding.videoViewVisualizer.pause()
                }
            }
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

            // Start video only if music is already playing when video becomes prepared
            if (isBound && musicService?.isPlayingState?.value == true) {
                binding.videoViewVisualizer.start()
                Log.d("VisualizerFragment", "Video started on prepare because music is playing.")
            } else {
                Log.d("VisualizerFragment", "Video prepared, but music not playing or service not bound. Video will not start yet.")
            }
        }

        binding.videoViewVisualizer.setOnErrorListener { _, what, extra ->
            Log.e("VisualizerFragment", "VideoView Error: what: $what, extra: $extra")
            videoIsPrepared = false
            true
        }

        binding.videoViewVisualizer.setOnClickListener {
            if (isBound && musicService != null) {
                if (musicService!!.isPlaying()) {
                    musicService!!.pauseTrack()
                    Log.d("VisualizerFragment", "Visualizer touched: Pausing track.")
                } else {
                    if (musicService!!.currentTrack != null) {
                        musicService!!.playTrackAtIndex(musicService!!.currentTrackIndex)
                        Log.d("VisualizerFragment", "Visualizer touched: Playing current track.")
                    } else {
                        // TODO: Implement robust playCurrentOrFirstTrack logic in MusicService,
                        // including getPlaylistSize, and uncomment/refine this block.
                        // For now, if there's no current track, we just show a message.
                        /*
                        if (musicService!!.getPlaylistSize() > 0) { // This method needs to be implemented in MusicService
                            musicService!!.playTrackAtIndex(0)
                            Log.d("VisualizerFragment", "Visualizer touched: Playing first track.")
                        } else {
                            Log.d("VisualizerFragment", "Visualizer touched: No track available to play.")
                            Toast.makeText(requireContext(), "No track to play", Toast.LENGTH_SHORT).show()
                        }
                        */
                        Log.d("VisualizerFragment", "Visualizer touched: No current track. MusicService would need to decide to play first track.")
                        Toast.makeText(requireContext(), "No track to play", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.d("VisualizerFragment", "Visualizer touched, but service not bound.")
                Toast.makeText(requireContext(), "Service not connected", Toast.LENGTH_SHORT).show()
            }
        }
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
        Log.d("VisualizerFragment", "onResume called. isBound: $isBound, musicPlaying: ${musicService?.isPlayingState?.value}, videoIsPrepared: $videoIsPrepared")
        // VideoView might have been paused or stopped.
        // Ensure it's playing if music is playing and video is prepared.
        if (isBound && musicService?.isPlayingState?.value == true && videoIsPrepared) {
            if (!binding.videoViewVisualizer.isPlaying) {
                binding.videoViewVisualizer.start()
                Log.d("VisualizerFragment", "Video explicitly started in onResume.")
            }
        } else if (videoIsPrepared && binding.videoViewVisualizer.isPlaying) {
            // If music is not playing, but video is, pause it.
            binding.videoViewVisualizer.pause()
            Log.d("VisualizerFragment", "Video explicitly paused in onResume as music is not playing.")
        }
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
