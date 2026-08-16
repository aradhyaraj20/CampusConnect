package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemFlashEventBinding
import com.example.campusconnect.models.Event
import java.util.concurrent.TimeUnit

class FlashEventAdapter(
    private var events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<FlashEventAdapter.FlashViewHolder>() {

    class FlashViewHolder(val binding: ItemFlashEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashViewHolder {
        val binding = ItemFlashEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FlashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FlashViewHolder, position: Int) {
        val event = events[position]
        holder.binding.flashTitle.text = event.title
        
        val remainingMs = (event.expiry_time ?: 0) - System.currentTimeMillis()
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        holder.binding.flashExpiry.text = "Ends in ${minutes}m"
        
        // Load flash event image using Coil
        if (event.image_uri != null) {
            holder.binding.flashImage.load(event.image_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_campus_logo)
                error(R.drawable.ic_campus_logo)
            }
        } else {
            holder.binding.flashImage.setImageResource(R.drawable.ic_launcher_background)
        }
        
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
