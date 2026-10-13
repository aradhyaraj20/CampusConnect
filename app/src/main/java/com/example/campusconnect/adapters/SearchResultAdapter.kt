package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemSearchResultBinding
import com.example.campusconnect.repository.SearchResult

class SearchResultAdapter(
    private var results: List<SearchResult>,
    private val onEventClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    class SearchResultViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val result = results[position]
        val event = result.event

        holder.binding.eventTitle.text = event.title
        holder.binding.eventDetails.text = "${event.date} · ${event.location}"

        if (event.image_uri != null) {
            holder.binding.eventImage.load(event.image_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_campus_logo)
                error(R.drawable.ic_campus_logo)
            }
        } else {
            holder.binding.eventImage.setImageResource(R.drawable.ic_launcher_background)
        }

        if (result.reason != null) {
            holder.binding.matchReason.text = "✨ ${result.reason}"
            holder.binding.matchReason.visibility = View.VISIBLE
        } else {
            holder.binding.matchReason.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onEventClick(result) }
    }

    override fun getItemCount() = results.size

    fun updateResults(newResults: List<SearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }
}
