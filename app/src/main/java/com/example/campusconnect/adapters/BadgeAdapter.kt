package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.databinding.ItemBadgeBinding
import com.example.campusconnect.models.Badge

class BadgeAdapter(private var badges: List<Badge>) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    class BadgeViewHolder(val binding: ItemBadgeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val binding = ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BadgeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badges[position]
        holder.binding.badgeName.text = badge.name
        holder.binding.badgeDescription.text = badge.description
        // In a real app, load icon from resource/uri
    }

    override fun getItemCount() = badges.size

    fun updateBadges(newBadges: List<Badge>) {
        badges = newBadges
        notifyDataSetChanged()
    }
}
