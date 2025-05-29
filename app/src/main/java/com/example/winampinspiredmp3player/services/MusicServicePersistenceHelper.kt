package com.example.winampinspiredmp3player.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.winampinspiredmp3player.data.Track // Assuming Track data class is accessible

object MusicServicePersistenceHelper {

    private const val PREFS_NAME = "WinampMediaPlayerState"
    private const val KEY_LAST_TRACK_URI = "lastTrackUri"
    private const val KEY_LAST_TRACK_POSITION = "lastTrackPosition"
    // Potentially add keys for other track metadata if needed for standalone URI restoration
    // private const val KEY_LAST_TRACK_TITLE = "lastTrackTitle" 

    data class LastPlayedState(val uri: Uri, val position: Int)

    fun savePlaybackState(context: Context, currentTrack: Track?, currentPositionMillis: Int?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (currentTrack != null && currentPositionMillis != null) {
            prefs.putString(KEY_LAST_TRACK_URI, currentTrack.uri.toString())
            prefs.putInt(KEY_LAST_TRACK_POSITION, currentPositionMillis)
            // prefs.putString(KEY_LAST_TRACK_TITLE, currentTrack.title) // Example
            Log.d("MusicServicePersistence", "Saved state: URI=${currentTrack.uri}, Pos=$currentPositionMillis")
        } else {
            prefs.remove(KEY_LAST_TRACK_URI)
            prefs.remove(KEY_LAST_TRACK_POSITION)
            // prefs.remove(KEY_LAST_TRACK_TITLE)
            Log.d("MusicServicePersistence", "Cleared saved playback state.")
        }
        prefs.apply()
    }

    fun loadPlaybackState(context: Context): LastPlayedState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_LAST_TRACK_URI, null)
        if (uriString != null) {
            val position = prefs.getInt(KEY_LAST_TRACK_POSITION, 0)
            // val title = prefs.getString(KEY_LAST_TRACK_TITLE, null) // Example
            Log.d("MusicServicePersistence", "Loaded state: URI=$uriString, Pos=$position")
            return LastPlayedState(Uri.parse(uriString), position)
        }
        Log.d("MusicServicePersistence", "No saved playback state found.")
        return null
    }
}
