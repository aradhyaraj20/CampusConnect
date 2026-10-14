package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemEventBinding
import com.example.campusconnect.models.Event

class EventAdapter(
    private var events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    /** eventId -> RSVP status ("going" / "waitlisted") for the signed-in user. */
    private var rsvpStatuses: Map<Int, String> = emptyMap()

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

        val ctx = holder.itemView.context
        holder.binding.goingCount.text = when (rsvpStatuses[event.id]) {
            "going" -> ctx.getString(R.string.event_joined)
            "waitlisted" -> ctx.getString(R.string.event_waitlisted)
            else -> ctx.getString(R.string.event_join_now)
        }
        holder.itemView.contentDescription = holder.itemView.context.getString(
            R.string.cd_open_event
        ) + ": " + event.title + ". " + event.date + " " + event.location
        holder.binding.eventImage.contentDescription = event.title
        
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateRsvpStatuses(statuses: Map<Int, String>) {
        rsvpStatuses = statuses
        notifyDataSetChanged()
    }

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}

/** Keeps the Join/Joined label of every row in sync with the user's RSVPs while [owner] is started. */
fun EventAdapter.trackRsvps(context: android.content.Context, owner: androidx.lifecycle.LifecycleOwner) {
    val userId = com.example.campusconnect.core.security.SessionManager.getInstance(context).getCurrentUserId()
    if (userId == -1) return
    val dao = com.example.campusconnect.database.AppDatabase.getDatabase(context).rsvpDao()
    owner.lifecycleScope.launch {
        dao.getRsvpsForUser(userId).collectLatest { rsvps ->
            updateRsvpStatuses(rsvps.associate { it.event_id to it.status })
        }
    }
}
