package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemRecapPhotoBinding
import com.example.campusconnect.models.RecapPhoto

class RecapPhotoAdapter(
    private val onLongPress: (RecapPhoto) -> Unit
) : ListAdapter<RecapPhoto, RecapPhotoAdapter.PhotoViewHolder>(Diff) {

    class PhotoViewHolder(val binding: ItemRecapPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemRecapPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = getItem(position)
        holder.binding.recapImage.load(photo.image_uri) {
            crossfade(true)
            placeholder(R.drawable.ic_campus_logo)
            error(R.drawable.ic_campus_logo)
        }
        holder.itemView.setOnLongClickListener {
            onLongPress(photo)
            true
        }
    }

    private object Diff : DiffUtil.ItemCallback<RecapPhoto>() {
        override fun areItemsTheSame(a: RecapPhoto, b: RecapPhoto) = a.id == b.id
        override fun areContentsTheSame(a: RecapPhoto, b: RecapPhoto) = a == b
    }
}
