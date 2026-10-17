package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import coil.dispose
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
        holder.binding.eventDetails.text = event.date
        holder.binding.eventLocation.text = event.location
        val hasLocation = event.location.isNotBlank()
        holder.binding.eventLocation.visibility = if (hasLocation) View.VISIBLE else View.GONE
        holder.binding.eventLocationIcon.visibility = if (hasLocation) View.VISIBLE else View.GONE

        holder.binding.eventCategory.text = event.category
        holder.binding.eventCategory.visibility =
            if (event.category.isNotBlank()) View.VISIBLE else View.GONE

        holder.binding.eventImage.loadEventImage(event.image_uri)

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

/**
 * Loads an event/ad image. The neutral placeholder (bg_image_placeholder) is the ImageView's
 * background, so while loading, on error, or with no image the view is left empty and the
 * placeholder shows through unscaled (a drawable passed to placeholder()/error() would be
 * blown up by centerCrop).
 */
internal fun ImageView.loadEventImage(uri: String?) {
    if (uri.isNullOrBlank()) {
        dispose()
        setImageDrawable(null)
        return
    }
    load(uri) {
        crossfade(true)
        placeholder(null as android.graphics.drawable.Drawable?)
        error(null as android.graphics.drawable.Drawable?)
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
