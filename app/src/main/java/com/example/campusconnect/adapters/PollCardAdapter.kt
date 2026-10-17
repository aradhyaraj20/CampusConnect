package com.example.campusconnect.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemPollCardBinding
import com.example.campusconnect.databinding.ItemPollOptionBinding
import com.example.campusconnect.models.Poll
import com.example.campusconnect.models.PollOption
import com.google.android.material.color.MaterialColors

/** A poll with its options and the option the viewer picked (null = not voted yet). */
data class PollCard(val poll: Poll, val options: List<PollOption>, val myOptionId: Int?)

class PollCardAdapter(
    private val onVote: (Poll, PollOption) -> Unit
) : ListAdapter<PollCard, PollCardAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemPollCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPollCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val res = ctx.resources
        val total = card.options.sumOf { it.vote_count }
        val closed = card.poll.isClosed()

        b.pollQuestion.text = card.poll.question
        val votes = res.getQuantityString(R.plurals.poll_vote_count, total, total)
        val deadline = when {
            closed -> ctx.getString(R.string.poll_closed)
            card.poll.ends_at.isBlank() -> ctx.getString(R.string.poll_no_deadline)
            else -> ctx.getString(R.string.poll_ends, card.poll.ends_at)
        }
        b.pollStats.text = ctx.getString(R.string.poll_stats_format, votes, deadline)

        b.optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        val highlight = MaterialColors.getColor(b.root, androidx.appcompat.R.attr.colorPrimary)
        card.options.forEach { option ->
            val row = ItemPollOptionBinding.inflate(inflater, b.optionsContainer, false)
            val percent = if (total == 0) 0 else option.vote_count * 100 / total
            val mine = option.id == card.myOptionId
            row.optionText.text = if (mine) ctx.getString(R.string.poll_option_mine, option.option_text) else option.option_text
            row.voteProgress.progress = percent
            row.votePercentage.text = ctx.getString(R.string.percent_format, percent)
            if (mine) {
                row.root.strokeColor = highlight
                row.root.strokeWidth = res.getDimensionPixelSize(R.dimen.poll_selected_stroke)
            }
            row.root.contentDescription = ctx.getString(R.string.cd_poll_option, option.option_text, percent)
            val canVote = !closed && card.myOptionId == null
            row.root.isClickable = canVote
            row.root.isFocusable = canVote
            if (canVote) row.root.setOnClickListener { onVote(card.poll, option) }
            b.optionsContainer.addView(row.root)
        }
    }

    private object Diff : DiffUtil.ItemCallback<PollCard>() {
        override fun areItemsTheSame(a: PollCard, b: PollCard) = a.poll.id == b.poll.id
        override fun areContentsTheSame(a: PollCard, b: PollCard) = a == b
    }
}
