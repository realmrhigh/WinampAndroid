package com.example.winampinspiredmp3player.ui.playlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.winampinspiredmp3player.data.Track
import com.example.winampinspiredmp3player.databinding.ListItemTrackBinding
import java.util.concurrent.TimeUnit

class PlaylistAdapter(
    private var tracks: MutableList<Track>,
    private val onTrackClickListener: (Track, Int, List<Track>) -> Unit // New constructor parameter
) : RecyclerView.Adapter<PlaylistAdapter.TrackViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ListItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track)
        // Set click listener on the item view
        holder.itemView.setOnClickListener {
            onTrackClickListener(track, holder.adapterPosition, tracks)
        }
    }

    override fun getItemCount(): Int = tracks.size

    fun updateTracks(newTracks: List<Track>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    inner class TrackViewHolder(private val binding: ListItemTrackBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track) {
            binding.tvTrackTitle.text = track.title ?: track.fileName // Show filename if title is null
            binding.tvTrackArtist.text = track.artist ?: "Unknown Artist"
            binding.tvTrackDuration.text = formatDuration(track.duration)
        }

        private fun formatDuration(millis: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
