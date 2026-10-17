package com.example.campusconnect.core.common

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * In-app feedback via Snackbar instead of Toast. Snackbars are tied to the screen,
 * so use a Toast only when the activity is about to finish().
 */
fun Activity.showMessage(@StringRes res: Int, long: Boolean = false, anchor: View? = null) =
    showMessage(getString(res), long, anchor)

fun Activity.showMessage(text: CharSequence, long: Boolean = false, anchor: View? = null) {
    val root = anchor ?: findViewById<View>(android.R.id.content) ?: return
    Snackbar.make(root, text, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
}

fun Fragment.showMessage(@StringRes res: Int, long: Boolean = false) =
    showMessage(getString(res), long)

fun Fragment.showMessage(text: CharSequence, long: Boolean = false) {
    val root = view ?: activity?.findViewById<View>(android.R.id.content) ?: return
    Snackbar.make(root, text, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
}
