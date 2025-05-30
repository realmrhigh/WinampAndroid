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
import com.example.winampinspiredmp3player.R // Ensure R is imported for string resources
import com.example.winampinspiredmp3player.data.Track
import com.example.winampinspiredmp3player.databinding.FragmentPlaylistBinding
import com.example.winampinspiredmp3player.services.MusicService

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var playlistAdapter: PlaylistAdapter
    private val musicTracks = mutableListOf<Track>() // This will be the working list (displayed, potentially shuffled)
    private val originalMusicTracks: MutableList<Track> = mutableListOf() // Master list, always in scanned/sorted order (after filtering)
    private val allScannedTracks: MutableList<Track> = mutableListOf() // Holds all tracks from MediaStore before any filtering
    private var isShuffleEnabled: Boolean = false
    private var filterShortTracksEnabled: Boolean = false

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
        Log.d("PlaylistFragment", "onViewCreated: View creation started.");
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView() // Initialize adapter here
        setupSortSpinner() // Setup spinner for sorting
        binding.btnScanMusic.setOnClickListener {
            checkAndRequestPermission()
        }
        binding.btnToggleShuffle.setOnClickListener {
            toggleShuffle()
        }

        binding.switchFilterShortTracks.isChecked = filterShortTracksEnabled
        binding.switchFilterShortTracks.setOnCheckedChangeListener { _, isChecked ->
            filterShortTracksEnabled = isChecked
            Log.d("PlaylistFragment", "Filter switch toggled. Enabled: $filterShortTracksEnabled")
            Toast.makeText(requireContext(), "Filter <1 min: ${if (filterShortTracksEnabled) "On" else "Off"}", Toast.LENGTH_SHORT).show()
            applyFiltersAndRefreshList()
        }

        checkAndRequestPermission() // Auto-scan on view created
        Log.d("PlaylistFragment", "onViewCreated: Called checkAndRequestPermission for auto-scan.");
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
        // Sort originalMusicTracks
        when (sortOption) {
            getString(R.string.sort_alphabetical) -> {
                Log.d("PlaylistFragment", "Sorting original by alphabetical")
                originalMusicTracks.sortBy { it.title?.lowercase() ?: it.fileName.lowercase() }
            }
            getString(R.string.sort_date_newest) -> {
                Log.d("PlaylistFragment", "Sorting original by date newest")
                originalMusicTracks.sortByDescending { it.dateAdded }
            }
            getString(R.string.sort_date_oldest) -> {
                Log.d("PlaylistFragment", "Sorting original by date oldest")
                originalMusicTracks.sortBy { it.dateAdded }
            }
            getString(R.string.sort_duration_shortest) -> {
                Log.d("PlaylistFragment", "Sorting original by duration (shortest first)")
                originalMusicTracks.sortBy { it.duration }
            }
            getString(R.string.sort_duration_longest) -> {
                Log.d("PlaylistFragment", "Sorting original by duration (longest first)")
                originalMusicTracks.sortByDescending { it.duration }
            }
        }

        // Update musicTracks based on shuffle state
        if (isShuffleEnabled) {
            musicTracks.clear()
            musicTracks.addAll(originalMusicTracks.shuffled())
            Log.d("PlaylistFragment", "Applied shuffle to sorted original list.")
        } else {
            musicTracks.clear()
            musicTracks.addAll(originalMusicTracks)
            Log.d("PlaylistFragment", "Set musicTracks to sorted original list (shuffle off).")
        }
        playlistAdapter.updateTracks(musicTracks)

        // Notify MusicService about the playlist reordering
        // This is important if playback is ongoing or if the service maintains its own copy of the playlist order.
        if (isBound && musicService != null) {
            // TODO: Implement updatePlaylistOrder in MusicService and uncomment this line
            // musicService?.updatePlaylistOrder(ArrayList(musicTracks)) // Pass a copy
            Log.d("PlaylistFragment", "Notified MusicService of playlist reorder for sort: $sortOption (currently commented out)")
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
                    // (activity as? MainActivity)?.switchToPlayerTab() // Removed as per subtask
                } else {
                    Log.w("PlaylistFragment", "Track clicked but service not bound or null.")
                    Toast.makeText(requireContext(), "Music service not ready. Try again.", Toast.LENGTH_SHORT).show()
                }
            },
            { track, position -> // onRemoveClick
                val trackToRemove = musicTracks[position] // Get track object before adapter modifies musicTracks
                Log.d("PlaylistFragment", "Attempting to remove track: ${trackToRemove.title} at displayed position $position")

                // TODO: Implement removeTrack(track: Track) in MusicService and uncomment this line
                // musicService?.removeTrack(trackToRemove)

                playlistAdapter.removeItem(position) // This removes from adapter's list (this.musicTracks)

                val removedFromOriginal = originalMusicTracks.remove(trackToRemove)
                Log.d("PlaylistFragment", "Track ${trackToRemove.title} removed from originalMusicTracks: $removedFromOriginal")

                val removedFromAllScanned = allScannedTracks.remove(trackToRemove)
                Log.d("PlaylistFragment", "Track ${trackToRemove.title} removed from allScannedTracks: $removedFromAllScanned")

                Toast.makeText(requireContext(), "${trackToRemove.title} removed", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvPlaylist.apply {
            adapter = playlistAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        Log.d("PlaylistFragment", "setupRecyclerView: PlaylistAdapter initialized. Initial item count from musicTracks: ${musicTracks.size}");
        Log.d("PlaylistFragment", "setupRecyclerView: RecyclerView setup complete. Adapter item count: ${binding.rvPlaylist.adapter?.itemCount}");
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

        // val selection = "${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.MIME_TYPE} = ?"
        // val selectionArgs = arrayOf("audio/mpeg", "audio/wav")
        val selection: String? = null
        val selectionArgs: Array<String>? = null

        Log.d("PlaylistFragment", "scanForMusicFiles: Starting scan. URI: ${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}");
        val query = requireContext().contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection, // Changed to null
            selectionArgs, // Changed to null
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
                Log.d("PlaylistFragment", "scanForMusicFiles: Found track: ${track.title} - ${track.fileName}, DateAdded: ${track.dateAdded}");

                if (tracksLogged < 3) { // Log first 3 tracks for verification
                    Log.d("PlaylistFragment", "Sample Track ${tracksLogged + 1}: URI=${track.uri}, Title=${track.title}, Artist=${track.artist}, Duration=${track.duration}, FileName=${track.fileName}, DateAdded=${track.dateAdded}")
                    tracksLogged++
                }
            }
        }
        Log.d("PlaylistFragment", "scanForMusicFiles: Scan complete. Found ${currentTracks.size} tracks initially.");

        allScannedTracks.clear()
        allScannedTracks.addAll(currentTracks) // Store all scanned tracks
        Log.d("PlaylistFragment", "scanForMusicFiles: Stored ${allScannedTracks.size} tracks in allScannedTracks.")

        applyFiltersAndRefreshList() // New method call to apply filters and then sort/shuffle
    }

    private fun applyFiltersAndRefreshList() {
        Log.d("PlaylistFragment", "applyFiltersAndRefreshList: Applying filter (enabled: $filterShortTracksEnabled)")
        val filteredList = if (filterShortTracksEnabled) {
            allScannedTracks.filter { it.duration >= 60000L } // 60000L for Long comparison
        } else {
            ArrayList(allScannedTracks) // Create a new list instance from all scanned tracks
        }
        Log.d("PlaylistFragment", "applyFiltersAndRefreshList: Filtered list size: ${filteredList.size}")

        originalMusicTracks.clear()
        originalMusicTracks.addAll(filteredList)
        Log.d("PlaylistFragment", "applyFiltersAndRefreshList: originalMusicTracks updated. Size: ${originalMusicTracks.size}")

        // Now call the existing method that sorts originalMusicTracks, applies shuffle to musicTracks, and updates adapter
        sortPlaylistBasedOnSpinner()

        // Display toast based on the final musicTracks list (which is what the adapter shows)
        if (musicTracks.isEmpty()) {
            Toast.makeText(requireContext(), "No music files to display.", Toast.LENGTH_SHORT).show()
            Log.d("PlaylistFragment", "applyFiltersAndRefreshList: No music files to display message shown.");
        } else {
            // Toast for scan completion is already shown in scanForMusicFiles,
            // this log is for after filtering/sorting.
            Log.d("PlaylistFragment", "applyFiltersAndRefreshList: Playlist updated. Displaying ${musicTracks.size} tracks.");
        }
    }


    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        // Update button appearance (e.g., tint if active) - TODO: Implement visual feedback for shuffle state
        if (isShuffleEnabled) {
            musicTracks.clear()
            musicTracks.addAll(originalMusicTracks.shuffled())
            Toast.makeText(requireContext(), "Shuffle On", Toast.LENGTH_SHORT).show()
            Log.d("PlaylistFragment", "Shuffle enabled. musicTracks count: ${musicTracks.size}")
        } else {
            // Revert to current sort order from originalMusicTracks
            musicTracks.clear()
            musicTracks.addAll(originalMusicTracks) // originalMusicTracks should still be sorted as per last spinner selection
            Toast.makeText(requireContext(), "Shuffle Off", Toast.LENGTH_SHORT).show()
            Log.d("PlaylistFragment", "Shuffle disabled. Restored sorted list from original. musicTracks count: ${musicTracks.size}")
        }
        playlistAdapter.updateTracks(musicTracks)

        // TODO: Notify MusicService of new list order and possibly update current playing index
        // musicService?.updatePlaylistOrder(ArrayList(this.musicTracks))
        // Log.d("PlaylistFragment", "Notified MusicService of playlist reorder due to shuffle toggle.")
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
