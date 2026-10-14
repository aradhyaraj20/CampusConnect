package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.example.campusconnect.models.Message

/**
 * Chat bubbles: own messages on the right, others on the left with the sender's name
 * shown once per run of consecutive messages from the same person.
 */
class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: Int = -1,
    private var names: Map<Int, String> = emptyMap()
) : RecyclerView.Adapter<MessageAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView? = view.findViewById(R.id.senderTextView)
        val content: TextView = view.findViewById(R.id.contentTextView)
        val time: TextView = view.findViewById(R.id.timeTextView)
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].user_id == currentUserId) TYPE_MINE else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == TYPE_MINE) R.layout.item_message_mine else R.layout.item_message
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val message = messages[position]
        holder.content.text = message.content
        holder.time.text = message.sent_at
        holder.sender?.let { sender ->
            val startsRun = position == 0 || messages[position - 1].user_id != message.user_id
            sender.visibility = if (startsRun) View.VISIBLE else View.GONE
            sender.text = names[message.user_id]
                ?: sender.context.getString(R.string.chat_unknown_user)
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<Message>, newNames: Map<Int, String> = names) {
        messages = newMessages
        names = newNames
        notifyDataSetChanged()
    }

    private companion object {
        const val TYPE_MINE = 1
        const val TYPE_OTHER = 2
    }
}
