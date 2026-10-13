package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemRecommendationBinding
import com.example.campusconnect.models.Event

class RecommendationAdapter(
    private var events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    class RecommendationViewHolder(val binding: ItemRecommendationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecommendationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        val event = events[position]
        holder.binding.recommendationTitle.text = event.title
        holder.binding.recommendationCategory.text = event.category
        
        if (event.image_uri != null) {
            holder.binding.recommendationImage.load(event.image_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_campus_logo)
                error(R.drawable.ic_campus_logo)
            }
        }
        
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
