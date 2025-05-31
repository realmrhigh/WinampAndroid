package com.example.winampinspiredmp3player.ui.playlist

import androidx.recyclerview.widget.DiffUtil
import com.example.winampinspiredmp3player.data.Track

class TrackDiffCallback(
    private val oldList: List<Track>,
    private val newList: List<Track>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Assuming track.uri is a unique and stable identifier
        return oldList[oldItemPosition].uri == newList[newItemPosition].uri
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Track is a data class, so its equals() method compares all properties.
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
