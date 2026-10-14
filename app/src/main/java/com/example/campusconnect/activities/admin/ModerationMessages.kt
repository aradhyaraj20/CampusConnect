package com.example.campusconnect.activities.admin

import android.content.Context
import android.widget.Toast
import com.example.campusconnect.R
import com.example.campusconnect.domain.usecase.ModerationOutcome

/** Shows [applied] on success, otherwise the reason the action was refused. */
fun ModerationOutcome.toast(context: Context, applied: String) {
    val message = when (this) {
        ModerationOutcome.APPLIED -> applied
        ModerationOutcome.NOT_FOUND -> context.getString(R.string.moderation_target_not_found)
        ModerationOutcome.PROTECTED_ADMIN -> context.getString(R.string.moderation_admin_protected)
        ModerationOutcome.NO_CHANGE -> context.getString(R.string.moderation_no_change)
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
