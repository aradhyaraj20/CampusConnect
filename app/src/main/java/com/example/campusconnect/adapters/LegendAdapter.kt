package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemLegendBinding
import com.example.campusconnect.models.User

class LegendAdapter(private var legends: List<User>) : RecyclerView.Adapter<LegendAdapter.LegendViewHolder>() {

    class LegendViewHolder(val binding: ItemLegendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LegendViewHolder {
        val binding = ItemLegendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LegendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LegendViewHolder, position: Int) {
        val user = legends[position]
        holder.binding.legendName.text = user.name
        holder.binding.legendKarma.text = "${user.karma_points} pts"
        holder.binding.rankBadge.text = (position + 1).toString()

        if (user.avatar_uri != null) {
            holder.binding.legendImage.load(user.avatar_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }
        } else {
            holder.binding.legendImage.setImageResource(R.drawable.ic_user_placeholder)
        }
    }

    override fun getItemCount() = legends.size

    fun updateLegends(newLegends: List<User>) {
        legends = newLegends
        notifyDataSetChanged()
    }
}
