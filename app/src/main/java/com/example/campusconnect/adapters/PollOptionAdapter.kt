package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.databinding.ItemPollOptionBinding
import com.example.campusconnect.models.PollOption

class PollOptionAdapter(
    private var options: List<PollOption>,
    private val onOptionClick: (PollOption) -> Unit
) : RecyclerView.Adapter<PollOptionAdapter.PollOptionViewHolder>() {

    class PollOptionViewHolder(val binding: ItemPollOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollOptionViewHolder {
        val binding = ItemPollOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PollOptionViewHolder, position: Int) {
        val option = options[position]
        holder.binding.optionText.text = option.option_text
        
        val totalVotes = options.sumOf { it.vote_count }.coerceAtLeast(1)
        val percentage = (option.vote_count * 100) / totalVotes
        
        holder.binding.voteProgress.progress = percentage
        holder.binding.votePercentage.text = "$percentage%"
        
        holder.itemView.setOnClickListener { onOptionClick(option) }
    }

    override fun getItemCount() = options.size

    fun updateOptions(newOptions: List<PollOption>) {
        options = newOptions
        notifyDataSetChanged()
    }
}
