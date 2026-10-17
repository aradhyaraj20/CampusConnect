package com.example.campusconnect.adapters

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
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
        val b = holder.binding
        b.notificationTitle.text = notification.title
        b.notificationMessage.text = notification.message
        b.notificationTime.text = DateUtils.getRelativeTimeSpanString(
            notification.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        )
        b.notificationIcon.setImageResource(iconFor(notification.type))

        // Unread: bold title plus a dot. Read items stay at full contrast, just without the emphasis.
        val unread = !notification.is_read
        b.notificationTitle.setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
        b.unreadDot.visibility = if (unread) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(notification) }
    }

    @DrawableRes
    private fun iconFor(type: String): Int = when (type) {
        "karma" -> R.drawable.ic_star
        "flash" -> R.drawable.ic_warning
        "interest" -> R.drawable.ic_search
        "event_created" -> R.drawable.ic_add
        "event_update" -> R.drawable.ic_event
        else -> R.drawable.ic_notification
    }

    override fun getItemCount() = notifications.size

    fun updateNotifications(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }
}
