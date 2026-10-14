package com.example.campusconnect.core.common

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.campusconnect.R
import com.example.campusconnect.databinding.ItemStatCardBinding
import com.google.android.material.card.MaterialCardView

/**
 * Styles a KPI card like the student Quick Access tiles: pale tint background,
 * strong accent colour for the number, flat with 18dp corners.
 */
object StatCardStyle {
    fun apply(
        context: Context,
        card: ItemStatCardBinding,
        value: String,
        label: String,
        accentRes: Int,
        tintRes: Int
    ) {
        card.statValue.text = value
        card.statLabel.text = label
        card.statValue.setTextColor(ContextCompat.getColor(context, accentRes))
        card.statLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        (card.root as MaterialCardView).apply {
            setCardBackgroundColor(ContextCompat.getColor(context, tintRes))
            radius = resources.displayMetrics.density * 18
            cardElevation = 0f
        }
    }
}
