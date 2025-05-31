package com.example.winampinspiredmp3player.ui.settings

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.winampinspiredmp3player.MainActivity
import com.example.winampinspiredmp3player.R
import com.example.winampinspiredmp3player.data.Track // Assuming Track data class is accessible
import com.example.winampinspiredmp3player.databinding.FragmentSettingsBinding
import com.example.winampinspiredmp3player.ui.playlist.PlaylistFragment // For accessing its constants
import com.example.winampinspiredmp3player.ui.visualizer.VisualizerFragment // For accessing its constants
import androidx.core.content.edit

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var playlistPrefs: SharedPreferences
    private lateinit var visualizerPrefs: SharedPreferences
    private lateinit var playerPrefs: SharedPreferences

    // Define SharedPreferences constants
    companion object {
        const val PLAYER_PREFS_NAME = "player_prefs"
        const val KEY_AUTO_SAVE_STATE = "key_auto_save_state"
        // Re-using constants from PlaylistFragment and VisualizerFragment by referencing them directly
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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        playlistPrefs = requireActivity().getSharedPreferences(PlaylistFragment.PLAYLIST_PREFS_NAME, Context.MODE_PRIVATE)
        visualizerPrefs = requireActivity().getSharedPreferences(VisualizerFragment.VISUALIZER_PREFS_NAME, Context.MODE_PRIVATE)
        playerPrefs = requireActivity().getSharedPreferences(PLAYER_PREFS_NAME, Context.MODE_PRIVATE)

        setupScanMusicButton()
        setupSortOptionsSpinner()
        setupVisualizerSwitch()
        setupAutoLoadMusicSwitch()
        setupAutoSaveStateSwitch()

        // Setup Close Settings button
        val closeButton = view.findViewById<Button>(R.id.btn_close_settings)
        closeButton?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupScanMusicButton() {
        binding.btnScanMusicSettings.setOnClickListener {
            checkAndRequestPermission()
        }
    }

    private fun setupSortOptionsSpinner() {
        // Populate spinner
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.sort_options, // Make sure this array exists in strings.xml
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerSortOptionsSettings.adapter = adapter
        }

        // Load current sort preference and set spinner selection
        val currentSortOption = playlistPrefs.getString(PlaylistFragment.KEY_SORT_OPTION, PlaylistFragment.SORT_ALPHABETICAL)
        val sortOptionsArray = resources.getStringArray(R.array.sort_options)
        val currentPosition = sortOptionsArray.indexOf(currentSortOption).takeIf { it >= 0 } ?: 0
        binding.spinnerSortOptionsSettings.setSelection(currentPosition)

        // Set listener for item selection
        binding.spinnerSortOptionsSettings.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString()
                playlistPrefs.edit { putString(PlaylistFragment.KEY_SORT_OPTION, selectedOption) }
                Log.d("SettingsFragment", "Saved sort option: $selectedOption")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Interface callback
            }
        }
    }

    private fun setupVisualizerSwitch() {
        // Load current state and set switch
        val visualizerEnabled = visualizerPrefs.getBoolean(VisualizerFragment.KEY_VISUALIZER_ENABLED, false) // Default false
        binding.switchVisualizerEnabled.isChecked = visualizerEnabled

        // Set listener for state changes
        binding.switchVisualizerEnabled.setOnCheckedChangeListener { _, isChecked ->
            visualizerPrefs.edit {
                putBoolean(
                    VisualizerFragment.KEY_VISUALIZER_ENABLED,
                    isChecked
                )
            }
            Log.d("SettingsFragment", "Saved visualizer enabled: $isChecked")
            (activity as? MainActivity)?.onUserToggledVisualizerPreference()
        }
    }

    private fun setupAutoLoadMusicSwitch() {
        // Load current state and set switch
        val autoLoadEnabled = playlistPrefs.getBoolean(PlaylistFragment.KEY_AUTO_SCAN_ON_STARTUP, true) // Default true
        binding.switchAutoLoadMusic.isChecked = autoLoadEnabled

        // Set listener for state changes
        binding.switchAutoLoadMusic.setOnCheckedChangeListener { _, isChecked ->
            playlistPrefs.edit { putBoolean(PlaylistFragment.KEY_AUTO_SCAN_ON_STARTUP, isChecked) }
            Log.d("SettingsFragment", "Saved auto-load music: $isChecked")
        }
    }

    private fun setupAutoSaveStateSwitch() {
        // Load current state and set switch
        val autoSaveEnabled = playerPrefs.getBoolean(KEY_AUTO_SAVE_STATE, true) // Default true
        binding.switchAutoSaveState.isChecked = autoSaveEnabled

        // Set listener for state changes
        binding.switchAutoSaveState.setOnCheckedChangeListener { _, isChecked ->
            playerPrefs.edit { putBoolean(KEY_AUTO_SAVE_STATE, isChecked) }
            Log.d("SettingsFragment", "Saved auto-save state: $isChecked")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
        // This method is simplified from PlaylistFragment. It only scans and toasts.
        // It does not interact with PlaylistFragment's adapter or lists directly.
        val currentTracks = mutableListOf<Track>() // Temporary list for scanning
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection: String? = null
        val selectionArgs: Array<String>? = null

        Log.d("SettingsFragment", "scanForMusicFiles: Starting scan.")
        try {
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val duration = cursor.getLong(durationColumn)
                    val fileName = cursor.getString(displayNameColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn) * 1000L
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    currentTracks.add(Track(contentUri, title, artist, duration, fileName, dateAdded))
                }
            }
            Log.d("SettingsFragment", "scanForMusicFiles: Scan complete. Found ${currentTracks.size} tracks.")
            Toast.makeText(requireContext(), "Music scan complete. Found ${currentTracks.size} tracks. Go to playlist to see changes.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error during music scan: ${e.message}")
            Toast.makeText(requireContext(), "Error scanning music: ${e.message}", Toast.LENGTH_LONG).show()
        }
        // No direct update to PlaylistFragment from here. User navigates back.
        // PlaylistFragment's onResume logic (if it re-queries or observes changes) would handle updates.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d("SettingsFragment", "onDestroyView called, _binding set to null")
    }
}
