package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemMarketplaceBinding
import com.example.campusconnect.models.MarketplaceItem

class MarketplaceAdapter(
    private var items: List<MarketplaceItem>,
    private val onItemClick: (MarketplaceItem) -> Unit
) : RecyclerView.Adapter<MarketplaceAdapter.MarketplaceViewHolder>() {

    class MarketplaceViewHolder(val binding: ItemMarketplaceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketplaceViewHolder {
        val binding = ItemMarketplaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MarketplaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MarketplaceViewHolder, position: Int) {
        val item = items[position]
        holder.binding.itemTitle.text = item.title
        
        if (item.is_swap) {
            holder.binding.itemPrice.text = "Trade for: ${item.exchange_with}"
            holder.binding.itemPrice.setTextColor(holder.itemView.context.getColor(R.color.qa_orange))
        } else if (item.category == "Lost & Found") {
            holder.binding.itemPrice.text = "Karma Bounty: ${item.price.toInt()} pts"
            holder.binding.itemPrice.setTextColor(holder.itemView.context.getColor(R.color.tertiary))
        } else {
            holder.binding.itemPrice.text = "₹${item.price}"
            holder.binding.itemPrice.setTextColor(holder.itemView.context.getColor(R.color.link_accent))
        }
        
        holder.binding.itemPoster.text = "Posted by User ${item.poster_id}"
        
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<MarketplaceItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
