package com.example.winampinspiredmp3player.data

import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String?,
    val artist: String?,
    val duration: Long, // milliseconds
    val fileName: String
)
