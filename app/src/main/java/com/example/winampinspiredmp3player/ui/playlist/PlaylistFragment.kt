package com.example.winampinspiredmp3player.ui.playlist

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
        setupSortSpinner() // Setup spinner for sorting
        binding.btnScanMusic.setOnClickListener {
            checkAndRequestPermission()
        }
        checkAndRequestPermission() // Auto-scan on view created
    }

    private fun setupSortSpinner() {
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.spinnerSortOptions.adapter = adapter
        }

        binding.spinnerSortOptions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                view?.let { // Ensure view is not null, to prevent potential issues with item styling
                    val selectedOption = parent.getItemAtPosition(position).toString()
                    Log.d("PlaylistFragment", "Sort option selected: $selectedOption")
                    sortPlaylist(selectedOption)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }

    private fun sortPlaylist(sortOption: String) {
        when (sortOption) {
            getString(R.string.sort_alphabetical) -> { // "Alphabetical (A-Z)"
                musicTracks.sortBy { it.title?.lowercase() ?: it.fileName.lowercase() }
            }
            getString(R.string.sort_date_newest) -> { // "Date Added (Newest First)"
                musicTracks.sortByDescending { it.dateAdded }
            }
            getString(R.string.sort_date_oldest) -> { // "Date Added (Oldest First)"
                musicTracks.sortBy { it.dateAdded }
            }
        }
        playlistAdapter.updateTracks(musicTracks) // Update adapter with sorted list

        // Notify MusicService about the playlist reordering
        // This is important if playback is ongoing or if the service maintains its own copy of the playlist order.
        if (isBound && musicService != null) {
            musicService?.updatePlaylistOrder(ArrayList(musicTracks)) // Pass a copy
            Log.d("PlaylistFragment", "Notified MusicService of playlist reorder for sort: $sortOption")
        }
    }

    private fun sortPlaylistBasedOnSpinner() {
        if (binding.spinnerSortOptions.adapter != null && binding.spinnerSortOptions.selectedItemPosition != AdapterView.INVALID_POSITION) {
            val selectedOption = binding.spinnerSortOptions.selectedItem.toString()
            sortPlaylist(selectedOption)
        } else {
            // Default sort if spinner not ready or nothing selected (e.g. alphabetical)
            // This case might occur if scan finishes before spinner is fully initialized, though unlikely with current setup.
            sortPlaylist(getString(R.string.sort_alphabetical))
        }
    }


    private fun setupRecyclerView() {
        // Initialize adapter with the click listener
        playlistAdapter = PlaylistAdapter(musicTracks,
            { track, position, fullPlaylist -> // onTrackClick
                if (isBound && musicService != null) {
                    Log.d("PlaylistFragment", "Track clicked: ${track.title}, Position: $position")
                    musicService!!.setTrackList(fullPlaylist)
                    musicService!!.playTrackAtIndex(position)
                    (activity as? MainActivity)?.switchToPlayerTab()
                } else {
                    Log.w("PlaylistFragment", "Track clicked but service not bound or null.")
                    Toast.makeText(requireContext(), "Music service not ready. Try again.", Toast.LENGTH_SHORT).show()
                }
            },
            { track, position -> // onRemoveClick
                Log.d("PlaylistFragment", "Attempting to remove track: ${track.title} at position $position")
                // Inform the service to remove the track from its own list and handle playback adjustments
                musicService?.removeTrack(track) // Anticipate this method in MusicService

                // Tell the adapter to remove the item from its view and internal list
                // This will also modify 'musicTracks' in the fragment as they share the same list instance
                playlistAdapter.removeItem(position)

                Toast.makeText(requireContext(), "${track.title} removed", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvPlaylist.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                scanForMusicFiles()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                Toast.makeText(requireContext(), "Permission needed to access music files.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
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
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
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
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            Log.d("PlaylistFragment", "Found ${cursor.count} tracks.")
            var tracksLogged = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val duration = cursor.getLong(durationColumn)
                val fileName = cursor.getString(displayNameColumn) // This is MediaStore.Audio.Media.DISPLAY_NAME
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000L // Convert to milliseconds
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val track = Track(contentUri, title, artist, duration, fileName, dateAdded)
                currentTracks.add(track)

                if (tracksLogged < 3) { // Log first 3 tracks for verification
                    Log.d("PlaylistFragment", "Sample Track ${tracksLogged + 1}: URI=${track.uri}, Title=${track.title}, Artist=${track.artist}, Duration=${track.duration}, FileName=${track.fileName}, DateAdded=${track.dateAdded}")
                    tracksLogged++
                }
            }
        }
        // Update the fragment's own list (if needed for other purposes, though adapter now holds the primary list for click handling)
        musicTracks.clear()
        musicTracks.addAll(currentTracks)
        // Update the adapter's list - This will now happen after sorting
        // playlistAdapter.updateTracks(musicTracks) // Moved to after sortPlaylistBasedOnSpinner

        if (currentTracks.isEmpty()) {
            Toast.makeText(requireContext(), "No music files found.", Toast.LENGTH_SHORT).show()
            playlistAdapter.updateTracks(musicTracks) // Update with empty list if needed
        } else {
            Toast.makeText(requireContext(), "Found ${currentTracks.size} music files.", Toast.LENGTH_SHORT).show()
            // Apply initial sort based on current spinner selection (or default)
            // This ensures the list is sorted when first displayed after a scan.
            sortPlaylistBasedOnSpinner()
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
