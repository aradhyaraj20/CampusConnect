package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemFlashEventBinding
import com.example.campusconnect.models.Event
import java.util.concurrent.TimeUnit

class FlashEventAdapter(
    private var events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<FlashEventAdapter.FlashViewHolder>() {

    class FlashViewHolder(val binding: ItemFlashEventBinding) : RecyclerView.ViewHolder(binding.root) {
        /** Fixed card width from item_flash_event.xml, used whenever there is more than one item. */
        val carouselWidth: Int = binding.root.layoutParams?.width
            ?: (200 * binding.root.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashViewHolder {
        val binding = ItemFlashEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FlashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FlashViewHolder, position: Int) {
        val event = events[position]
        holder.binding.flashTitle.text = event.title
        
        val remainingMs = (event.expiry_time ?: 0) - System.currentTimeMillis()
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        holder.binding.flashExpiry.text = holder.itemView.context.getString(R.string.flash_ends_in, minutes.coerceAtLeast(0))
        
        holder.binding.flashImage.loadEventImage(event.image_uri)

        // A single live session fills the row instead of leaving the right half empty.
        val params = holder.itemView.layoutParams
        val targetWidth = if (itemCount == 1) ViewGroup.LayoutParams.MATCH_PARENT else holder.carouselWidth
        if (params != null && params.width != targetWidth) {
            params.width = targetWidth
            holder.itemView.layoutParams = params
        }
        
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
