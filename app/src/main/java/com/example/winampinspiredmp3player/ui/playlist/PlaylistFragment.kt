package com.example.winampinspiredmp3player.ui.playlist

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.winampinspiredmp3player.MainActivity
import com.example.winampinspiredmp3player.data.Track
import com.example.winampinspiredmp3player.databinding.FragmentPlaylistBinding
import com.example.winampinspiredmp3player.services.MusicService

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var playlistAdapter: PlaylistAdapter
    private val musicTracks = mutableListOf<Track>() // Keep this to hold scanned tracks before adapter update

    // Service related variables
    private var musicService: MusicService? = null
    private var isBound: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("PlaylistFragment", "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("PlaylistFragment", "Service Disconnected")
            musicService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                scanForMusicFiles()
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot scan for music.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView() // Initialize adapter here
        binding.btnScanMusic.setOnClickListener {
            checkAndRequestPermission()
        }
    }

    private fun setupRecyclerView() {
        // Initialize adapter with the click listener
        playlistAdapter = PlaylistAdapter(musicTracks) { track, position, fullPlaylist ->
            if (isBound && musicService != null) {
                Log.d("PlaylistFragment", "Track clicked: ${track.title}, Position: $position")
                musicService!!.setTrackList(fullPlaylist)
                musicService!!.playTrackAtIndex(position)
                (activity as? MainActivity)?.switchToPlayerTab()
            } else {
                Log.w("PlaylistFragment", "Track clicked but service not bound or null.")
                Toast.makeText(requireContext(), "Music service not ready. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvPlaylist.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun checkAndRequestPermission() {
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permissionToRequest
            ) == PackageManager.PERMISSION_GRANTED -> {
                scanForMusicFiles()
            }
            shouldShowRequestPermissionRationale(permissionToRequest) -> {
                Toast.makeText(requireContext(), "Permission needed to access music files.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permissionToRequest)
            }
            else -> {
                requestPermissionLauncher.launch(permissionToRequest)
            }
        }
    }

    private fun scanForMusicFiles() {
        val currentTracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg", "audio/wav")

        val query = requireContext().contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            Log.d("PlaylistFragment", "Found ${cursor.count} tracks.")
            var tracksLogged = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val duration = cursor.getLong(durationColumn)
                val fileName = cursor.getString(displayNameColumn) // This is MediaStore.Audio.Media.DISPLAY_NAME
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val track = Track(contentUri, title, artist, duration, fileName)
                currentTracks.add(track)

                if (tracksLogged < 3) { // Log first 3 tracks for verification
                    Log.d("PlaylistFragment", "Sample Track ${tracksLogged + 1}: URI=${track.uri}, Title=${track.title}, Artist=${track.artist}, Duration=${track.duration}, FileName=${track.fileName}")
                    tracksLogged++
                }
            }
        }
        // Update the adapter's list
        playlistAdapter.updateTracks(currentTracks)
        // Update the fragment's own list (if needed for other purposes, though adapter now holds the primary list for click handling)
        musicTracks.clear()
        musicTracks.addAll(currentTracks)


        if (currentTracks.isEmpty()) {
            Toast.makeText(requireContext(), "No music files found.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Found ${currentTracks.size} music files.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("PlaylistFragment", "onStart called, binding to service.")
        Intent(requireActivity(), MusicService::class.java).also { intent ->
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("PlaylistFragment", "onStop called, unbinding from service.")
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
            musicService = null // Good practice to nullify
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("PlaylistFragment", "onDestroyView called, _binding set to null")
    }
}
