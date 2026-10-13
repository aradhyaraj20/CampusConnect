package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemEventBinding
import com.example.campusconnect.models.Event

class EventAdapter(
    private var events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.binding.eventTitle.text = event.title
        holder.binding.eventDetails.text = "${event.date} · ${event.location}"
        
        // Load event image using Coil
        if (event.image_uri != null) {
            holder.binding.eventImage.load(event.image_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_campus_logo)
                error(R.drawable.ic_campus_logo)
            }
        } else {
            holder.binding.eventImage.setImageResource(R.drawable.ic_launcher_background)
        }

        // Placeholder for going count
        holder.binding.goingCount.text = "Join now"
        holder.itemView.contentDescription = holder.itemView.context.getString(
            R.string.cd_open_event
        ) + ": " + event.title + ". " + event.date + " " + event.location
        holder.binding.eventImage.contentDescription = event.title
        
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
