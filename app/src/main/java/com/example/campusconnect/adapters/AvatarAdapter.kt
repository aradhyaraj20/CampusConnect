package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemAvatarOptionBinding

class AvatarAdapter(
    private val avatarUrls: List<String>,
    private val onAvatarClick: (String) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    private var selectedPosition = -1

    class AvatarViewHolder(val binding: ItemAvatarOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val binding = ItemAvatarOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AvatarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val url = avatarUrls[position]
        holder.binding.avatarImage.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            error(R.drawable.ic_user_placeholder)
        }

        // Highlight selection
        if (selectedPosition == position) {
            holder.binding.root.strokeColor = holder.itemView.context.getColor(R.color.primary)
            holder.binding.root.setCardBackgroundColor(holder.itemView.context.getColor(R.color.surface_variant))
        } else {
            holder.binding.root.strokeColor = android.graphics.Color.TRANSPARENT
            holder.binding.root.setCardBackgroundColor(holder.itemView.context.getColor(R.color.white))
        }

        holder.itemView.setOnClickListener {
            // Look the position up fresh at click-time (not the bind-time value captured
            // above) so a stale index can't be used if the list changed in between.
            @Suppress("DEPRECATION")
            val clickedPosition = holder.adapterPosition
            if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val previousSelected = selectedPosition
            selectedPosition = clickedPosition
            if (previousSelected != -1) {
                notifyItemChanged(previousSelected)
            }
            notifyItemChanged(selectedPosition)
            onAvatarClick(avatarUrls[clickedPosition])
        }
    }

    fun getSelectedUrl(): String? {
        return if (selectedPosition != -1) avatarUrls[selectedPosition] else null
    }

    override fun getItemCount() = avatarUrls.size
}