package com.example.campusconnect.activities.admin

import android.app.Activity
import com.example.campusconnect.R
import com.example.campusconnect.core.common.showMessage
import com.example.campusconnect.domain.usecase.ModerationOutcome

/** Shows [applied] on success, otherwise the reason the action was refused, in a Snackbar. */
fun ModerationOutcome.showResult(activity: Activity, applied: String) {
    val message = when (this) {
        ModerationOutcome.APPLIED -> applied
        ModerationOutcome.NOT_FOUND -> activity.getString(R.string.moderation_target_not_found)
        ModerationOutcome.PROTECTED_ADMIN -> activity.getString(R.string.moderation_admin_protected)
        ModerationOutcome.NO_CHANGE -> activity.getString(R.string.moderation_no_change)
        ModerationOutcome.NOT_ALLOWED -> activity.getString(R.string.not_allowed)
    }
    activity.showMessage(message)
}
