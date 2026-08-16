package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemRecapPhotoBinding
import com.example.campusconnect.models.RecapPhoto

class RecapPhotoAdapter(private var photos: List<RecapPhoto>) : RecyclerView.Adapter<RecapPhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(val binding: ItemRecapPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemRecapPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        holder.binding.recapImage.load(photo.image_uri) {
            crossfade(true)
            placeholder(R.drawable.ic_campus_logo)
            error(R.drawable.ic_campus_logo)
        }
    }

    override fun getItemCount() = photos.size

    fun updatePhotos(newPhotos: List<RecapPhoto>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}
