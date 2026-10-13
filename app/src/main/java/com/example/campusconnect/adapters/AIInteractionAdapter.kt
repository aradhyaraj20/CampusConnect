package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.databinding.ItemAiMessageBinding
import com.example.campusconnect.databinding.ItemUserMessageBinding
import com.example.campusconnect.models.AIInteraction

class AIInteractionAdapter(private var history: List<AIInteraction>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (history[position].is_user_message) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER) {
            val binding = ItemUserMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemAiMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AIViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val interaction = history[position]
        if (holder is UserViewHolder) {
            holder.binding.userMessageText.text = interaction.prompt
        } else if (holder is AIViewHolder) {
            holder.binding.aiMessageText.text = interaction.response
        }
    }

    override fun getItemCount() = history.size

    fun updateHistory(newHistory: List<AIInteraction>) {
        history = newHistory
        notifyDataSetChanged()
    }

    class UserViewHolder(val binding: ItemUserMessageBinding) : RecyclerView.ViewHolder(binding.root)
    class AIViewHolder(val binding: ItemAiMessageBinding) : RecyclerView.ViewHolder(binding.root)
}
