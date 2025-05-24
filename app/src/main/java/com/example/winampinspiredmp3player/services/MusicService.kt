package com.example.winampinspiredmp3player.services

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.example.winampinspiredmp3player.data.Track

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()

    // New member variables
    private var trackList: List<Track> = emptyList()
    private var currentTrackIndex: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    var currentTrack: Track? = null // Publicly readable, for direct access if needed
        private set // Only service can set it

    // LiveData for UI updates
    val playbackPosition: MutableLiveData<Int> = MutableLiveData(0)
    val currentTrackDuration: MutableLiveData<Int> = MutableLiveData(0)
    val currentPlayingTrack: MutableLiveData<Track?> = MutableLiveData(null)


    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    playbackPosition.postValue(it.currentPosition)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        Log.d("MusicService", "Service Created, MediaPlayer Initialized")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("MusicService", "Service Bound")
        return binder
    }

    // Method to set the track list
    fun setTrackList(tracks: List<Track>) {
        this.trackList = tracks
        if (tracks.isNotEmpty()) {
            currentTrackIndex = -1 // Reset index, or to 0 if you want to auto-select first
            Log.d("MusicService", "Track list set with ${tracks.size} tracks.")
        } else {
            Log.d("MusicService", "Track list set to empty.")
        }
    }

    // Play track by URI (existing logic, now used by playTrackAtIndex)
    fun playTrack(trackUri: Uri) {
        Log.d("MusicService", "playTrack called with URI: $trackUri")
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop() // Stop current playback
                }
                reset()
                setDataSource(applicationContext, trackUri)
                setOnPreparedListener { mp -> // Renamed to mp for clarity
                    Log.d("MusicService", "MediaPlayer prepared, starting playback")
                    mp.start()
                    currentTrackDuration.postValue(mp.duration ?: 0)
                    handler.post(updateProgressRunnable) // Start progress updates
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("MusicService", "MediaPlayer Error: what: $what, extra: $extra")
                    mp.reset()
                    currentTrack = null
                    currentPlayingTrack.postValue(null)
                    handler.removeCallbacks(updateProgressRunnable)
                    true
                }
                setOnCompletionListener {
                    Log.d("MusicService", "Track completed. Playing next.")
                    playNextTrack()
                }
                prepareAsync()
                Log.d("MusicService", "MediaPlayer.prepareAsync() called")
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error setting data source or preparing MediaPlayer", e)
            currentTrack = null
            currentPlayingTrack.postValue(null)
            handler.removeCallbacks(updateProgressRunnable)
        }
    }

    // Play track by index from the trackList
    fun playTrackAtIndex(index: Int) {
        if (index >= 0 && index < trackList.size) {
            currentTrackIndex = index
            currentTrack = trackList[currentTrackIndex]
            currentPlayingTrack.postValue(currentTrack) // Update LiveData
            Log.d("MusicService", "playTrackAtIndex: $currentTrackIndex, Title: ${currentTrack?.title}")
            currentTrack?.uri?.let { playTrack(it) } // Call the URI based play method
        } else {
            Log.w("MusicService", "Invalid index $index for trackList size ${trackList.size}")
            currentTrack = null
            currentPlayingTrack.postValue(null)
            mediaPlayer?.reset() // Reset player if index is invalid
            handler.removeCallbacks(updateProgressRunnable)
        }
    }

    fun pauseTrack() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d("MusicService", "pauseTrack called")
                it.pause()
                handler.removeCallbacks(updateProgressRunnable) // Stop progress updates
            }
        }
    }

    fun stopTrack() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d("MusicService", "stopTrack called")
                it.stop()
            }
            it.reset()
            Log.d("MusicService", "MediaPlayer reset after stop")
        }
        currentTrack = null
        currentPlayingTrack.postValue(null) // Update LiveData
        handler.removeCallbacks(updateProgressRunnable) // Stop progress updates
        playbackPosition.postValue(0) // Reset playback position UI
        currentTrackDuration.postValue(0) // Reset duration UI
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    // New methods for Next/Previous
    fun playNextTrack() {
        if (trackList.isNotEmpty()) {
            currentTrackIndex++
            if (currentTrackIndex >= trackList.size) {
                currentTrackIndex = 0 // Loop to start
            }
            playTrackAtIndex(currentTrackIndex)
        } else {
            Log.d("MusicService", "Track list empty, cannot play next.")
        }
    }

    fun playPreviousTrack() {
        if (trackList.isNotEmpty()) {
            currentTrackIndex--
            if (currentTrackIndex < 0) {
                currentTrackIndex = trackList.size - 1 // Loop to end
            }
            playTrackAtIndex(currentTrackIndex)
        } else {
            Log.d("MusicService", "Track list empty, cannot play previous.")
        }
    }

    // New method for Seek
    fun seekTo(position: Int) {
        mediaPlayer?.let {
            if (it.isPlaying || it.isLooping || currentTrack != null) { // Check if player is in a valid state to seek
                it.seekTo(position)
                playbackPosition.postValue(it.currentPosition) // Update LiveData immediately
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "Service Destroyed, MediaPlayer Released")
        handler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
