package com.example.winampinspiredmp3player.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.MutableLiveData
import androidx.media.session.MediaButtonReceiver
import com.example.winampinspiredmp3player.MainActivity
import com.example.winampinspiredmp3player.R
import com.example.winampinspiredmp3player.data.Track

class MusicService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "com.example.winampinspiredmp3player.playback_channel"
        const val NOTIFICATION_ID = 1
    }

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat


    // Member variables
    private var trackList: List<Track> = emptyList()
    var currentTrackIndex: Int = -1
        private set
    var currentTrack: Track? = null
        private set

    private val handler = Handler(Looper.getMainLooper())

    // LiveData for UI updates
    val playbackPosition: MutableLiveData<Int> = MutableLiveData(0)
    val currentTrackDuration: MutableLiveData<Int> = MutableLiveData(0)
    val currentPlayingTrack: MutableLiveData<Track?> = MutableLiveData(null)
    val isPlayingState: MutableLiveData<Boolean> = MutableLiveData(false)


    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    playbackPosition.postValue(it.currentPosition)
                    updatePlaybackState()
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            Log.d("MusicService", "MediaSessionCallback: onPlay")
            if (currentTrack != null && mediaPlayer !=null && !mediaPlayer!!.isPlaying) {
                mediaPlayer!!.start()
                isPlayingState.postValue(true)
                handler.post(updateProgressRunnable)
                startForeground(NOTIFICATION_ID, buildNotification())
            } else if (currentTrack != null) {
                playTrackAtIndex(currentTrackIndex)
            }
            updatePlaybackState() // This will also trigger notification update
        }

        override fun onPause() {
            super.onPause()
            Log.d("MusicService", "MediaSessionCallback: onPause")
            pauseTrack()
        }

        override fun onStop() {
            super.onStop()
            Log.d("MusicService", "MediaSessionCallback: onStop")
            stopTrack()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            Log.d("MusicService", "MediaSessionCallback: onSkipToNext")
            playNextTrack()
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            Log.d("MusicService", "MediaSessionCallback: onSkipToPrevious")
            playPreviousTrack()
        }

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            Log.d("MusicService", "MediaSessionCallback: onSeekTo $pos")
            seekTo(pos.toInt())
        }
    }


    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        mediaPlayer = MediaPlayer()
        mediaSession = MediaSessionCompat(this, "WinampInspiredMP3PlayerSession")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.isActive = true
        Log.d("MusicService", "Service Created, MediaPlayer Initialized, MediaSession Active")
        updatePlaybackState()
        updateMediaMetadata()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Channel for music playback controls and information"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d("MusicService", "Notification channel created.")
        }
    }


    private fun buildNotification(): Notification {
        val controller = mediaSession.controller
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        builder
            .setContentTitle(metadata?.description?.title ?: "No Title")
            .setContentText(metadata?.description?.subtitle ?: "No Artist")
            .setSubText(metadata?.description?.description) // Album name if available
            .setLargeIcon(metadata?.description?.iconBitmap) // Album art
            .setSmallIcon(R.drawable.ic_music_note) // Placeholder for actual small icon
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)


        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingContentIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(pendingContentIntent)

        // Previous action
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_skip_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            )
        )

        // Play/Pause action
        if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause, "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
                )
            )
        } else {
            builder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_play_arrow, "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
                )
            )
        }

        // Next action
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_skip_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            )
        )

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2) // Previous, Play/Pause, Next
        )
        Log.d("MusicService", "Notification built. Title: ${metadata?.description?.title}")
        return builder.build()
    }


    override fun onBind(intent: Intent?): IBinder {
        Log.d("MusicService", "Service Bound")
        return binder
    }

    fun setTrackList(tracks: List<Track>) {
        this.trackList = tracks
        if (tracks.isNotEmpty()) {
            currentTrackIndex = -1
            Log.d("MusicService", "Track list set with ${tracks.size} tracks.")
        } else {
            Log.d("MusicService", "Track list set to empty.")
        }
    }

    private fun playTrack(trackUri: Uri) {
        Log.d("MusicService", "playTrack called with URI: $trackUri")
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    // stop() // Stop current playback -- This might be too aggressive. Reset handles it.
                }
                reset()
                setDataSource(applicationContext, trackUri)
                setOnPreparedListener { mp ->
                    Log.d("MusicService", "MediaPlayer prepared, starting playback")
                    mp.start()
                    isPlayingState.postValue(true)
                    currentTrackDuration.postValue(mp.duration ?: 0)
                    handler.post(updateProgressRunnable)
                    updatePlaybackState() // This will trigger notification update via its own logic
                    updateMediaMetadata() // This will also trigger notification update
                    startForeground(NOTIFICATION_ID, buildNotification()) // Start foreground with updated notification
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("MusicService", "MediaPlayer Error: what: $what, extra: $extra")
                    mp.reset()
                    currentTrack = null
                    currentPlayingTrack.postValue(null)
                    isPlayingState.postValue(false)
                    handler.removeCallbacks(updateProgressRunnable)
                    updatePlaybackState()
                    updateMediaMetadata()
                    stopForeground(true) // Remove notification on error
                    true
                }
                setOnCompletionListener {
                    Log.d("MusicService", "Track completed.")
                    isPlayingState.postValue(false)
                    // updatePlaybackState() // State before potentially playing next -- playNextTrack will handle it
                    playNextTrack() // This will eventually call playTrackAtIndex -> playTrack -> update states & notification
                }
                prepareAsync()
                // updatePlaybackState() // State is now preparing/buffering - called when actually playing or erroring
                Log.d("MusicService", "MediaPlayer.prepareAsync() called")
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error setting data source or preparing MediaPlayer", e)
            currentTrack = null
            currentPlayingTrack.postValue(null)
            isPlayingState.postValue(false)
            handler.removeCallbacks(updateProgressRunnable)
            updatePlaybackState()
            updateMediaMetadata()
            stopForeground(true)
        }
    }

    fun playTrackAtIndex(index: Int) {
        if (index >= 0 && index < trackList.size) {
            currentTrackIndex = index
            currentTrack = trackList[currentTrackIndex]
            currentPlayingTrack.postValue(currentTrack)
            Log.d("MusicService", "playTrackAtIndex: $currentTrackIndex, Title: ${currentTrack?.title}")
            currentTrack?.uri?.let {
                playTrack(it)
            }
        } else {
            Log.w("MusicService", "Invalid index $index for trackList size ${trackList.size}")
            currentTrack = null
            currentPlayingTrack.postValue(null)
            isPlayingState.postValue(false)
            mediaPlayer?.reset()
            handler.removeCallbacks(updateProgressRunnable)
            updatePlaybackState()
            updateMediaMetadata()
            stopForeground(true)
        }
    }

    fun pauseTrack() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d("MusicService", "pauseTrack called")
                it.pause()
                isPlayingState.postValue(false)
                handler.removeCallbacks(updateProgressRunnable)
                updatePlaybackState() // This will trigger notification update
                stopForeground(false) // Keep notification, but service is not foreground
                // notificationManager.notify(NOTIFICATION_ID, buildNotification()) // updatePlaybackState should handle this
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
        currentPlayingTrack.postValue(null)
        isPlayingState.postValue(false)
        handler.removeCallbacks(updateProgressRunnable)
        playbackPosition.postValue(0)
        currentTrackDuration.postValue(0)
        updatePlaybackState()
        updateMediaMetadata()
        stopForeground(true) // Remove notification
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun playNextTrack() {
        if (trackList.isNotEmpty()) {
            currentTrackIndex++
            if (currentTrackIndex >= trackList.size) {
                currentTrackIndex = 0
            }
            playTrackAtIndex(currentTrackIndex)
        } else {
            Log.d("MusicService", "Track list empty, cannot play next.")
            stopTrack() // Stop if list is empty
        }
    }

    fun playPreviousTrack() {
        if (trackList.isNotEmpty()) {
            currentTrackIndex--
            if (currentTrackIndex < 0) {
                currentTrackIndex = trackList.size - 1
            }
            playTrackAtIndex(currentTrackIndex)
        } else {
            Log.d("MusicService", "Track list empty, cannot play previous.")
            stopTrack() // Stop if list is empty
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.let {
            if (currentTrack != null) {
                it.seekTo(position)
                playbackPosition.postValue(it.currentPosition)
                updatePlaybackState()
            }
        }
    }

    private fun updatePlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
        var actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        val state: Int
        if (mediaPlayer == null) {
            state = PlaybackStateCompat.STATE_NONE
            actions = PlaybackStateCompat.ACTION_NONE
        } else if (mediaPlayer!!.isPlaying) {
            state = PlaybackStateCompat.STATE_PLAYING
            actions = actions or PlaybackStateCompat.ACTION_PAUSE // Can be paused
        } else if (currentTrack != null) {
            state = PlaybackStateCompat.STATE_PAUSED // Or ready to play
            actions = actions or PlaybackStateCompat.ACTION_PLAY // Can be played
        } else {
            state = PlaybackStateCompat.STATE_STOPPED
            actions = PlaybackStateCompat.ACTION_PLAY
        }

        stateBuilder.setActions(actions)
        stateBuilder.setState(
            state,
            mediaPlayer?.currentPosition?.toLong() ?: 0L,
            1.0f
        )
        mediaSession.setPlaybackState(stateBuilder.build())
        Log.d("MusicService", "PlaybackState updated: State=$state, Position=${mediaPlayer?.currentPosition}")

        // Update notification if not stopped (foreground service handles its own notification)
        if (state != PlaybackStateCompat.STATE_STOPPED && state != PlaybackStateCompat.STATE_NONE) {
            if(mediaPlayer?.isPlaying == false) { // Only if paused
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
            // If playing, startForeground will be called which updates notification
        }
    }


    private fun updateMediaMetadata() {
        val metadataBuilder = MediaMetadataCompat.Builder()
        if (currentTrack != null) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack?.title ?: currentTrack?.fileName)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack?.artist ?: "<Unknown Artist>")
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentTrack?.duration ?: 0L)
        } else {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Winamp MP3 Player")
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "No track playing")
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)

        }
        mediaSession.setMetadata(metadataBuilder.build())
        Log.d("MusicService", "MediaMetadata updated for track: ${currentTrack?.title}")

        // Update notification with new metadata if service is in a state where notification is visible but not foreground
        // (e.g., paused). If playing, startForeground will handle it. If stopped, notification is removed.
        if (mediaPlayer?.isPlaying == false && currentTrack != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "Service Destroyed, MediaPlayer and MediaSession Released")
        handler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }
}
