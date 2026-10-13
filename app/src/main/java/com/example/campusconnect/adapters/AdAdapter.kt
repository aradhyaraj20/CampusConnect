package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.databinding.ItemAdRowBinding
import com.example.campusconnect.models.Ad

class AdAdapter(
    private var ads: List<Ad>,
    private val onToggleActive: (Ad, Boolean) -> Unit,
    private val onDelete: (Ad) -> Unit
) : RecyclerView.Adapter<AdAdapter.Holder>() {

    class Holder(val binding: ItemAdRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAdRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ad = ads[position]
        holder.binding.adTitle.text = ad.title
        holder.binding.adMessage.text = ad.message

        if (!ad.image_uri.isNullOrBlank()) {
            holder.binding.adImage.visibility = View.VISIBLE
            holder.binding.adImage.load(ad.image_uri)
        } else {
            holder.binding.adImage.visibility = View.GONE
        }

        holder.binding.activeSwitch.setOnCheckedChangeListener(null)
        holder.binding.activeSwitch.isChecked = ad.is_active
        holder.binding.activeSwitch.setOnCheckedChangeListener { _, checked ->
            onToggleActive(ad, checked)
        }

        holder.binding.deleteAdButton.setOnClickListener { onDelete(ad) }
    }

    override fun getItemCount() = ads.size

    fun submit(newAds: List<Ad>) {
        ads = newAds
        notifyDataSetChanged()
    }
}
