package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemAttendeeMiniBinding
import com.example.campusconnect.models.User

class AttendeeAdapter(private var attendees: List<User>) : RecyclerView.Adapter<AttendeeAdapter.AttendeeViewHolder>() {

    class AttendeeViewHolder(val binding: ItemAttendeeMiniBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendeeViewHolder {
        val binding = ItemAttendeeMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendeeViewHolder, position: Int) {
        val user = attendees[position]
        if (user.avatar_uri != null) {
            holder.binding.attendeeImage.load(user.avatar_uri) {
                crossfade(true)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }
        } else {
            holder.binding.attendeeImage.setImageResource(R.drawable.ic_user_placeholder)
        }
    }

    override fun getItemCount() = attendees.size

    fun updateAttendees(newAttendees: List<User>) {
        attendees = newAttendees
        notifyDataSetChanged()
    }
}
