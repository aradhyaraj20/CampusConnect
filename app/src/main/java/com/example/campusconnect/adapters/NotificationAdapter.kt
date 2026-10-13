package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemNotificationBinding
import com.example.campusconnect.models.Notification

class NotificationAdapter(
    private var notifications: List<Notification>,
    private val onClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.binding.notificationTitle.text = notification.title
        holder.binding.notificationMessage.text = notification.message
        
        val iconRes = when(notification.type) {
            "karma" -> android.R.drawable.btn_star_big_on
            "flash" -> android.R.drawable.ic_dialog_alert
            "interest" -> android.R.drawable.ic_menu_search
            "event_created" -> android.R.drawable.ic_input_add
            else -> android.R.drawable.ic_popup_reminder
        }
        holder.binding.notificationIcon.setImageResource(iconRes)

        if (notification.is_read) {
            holder.binding.root.alpha = 0.5f
        } else {
            holder.binding.root.alpha = 1.0f
        }

        holder.itemView.setOnClickListener { onClick(notification) }
    }

    override fun getItemCount() = notifications.size

    fun updateNotifications(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }
}
