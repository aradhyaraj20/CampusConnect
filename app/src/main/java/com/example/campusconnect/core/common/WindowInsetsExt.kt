package com.example.campusconnect.core.common

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Makes a screen handle window insets itself so a bottom-pinned input bar
 * (chat / AI assistant) rides above the on-screen keyboard, and the toolbar
 * clears the status bar.
 *
 * Why this is needed on Android 15 (targetSdk 35): apps are edge-to-edge and
 * `windowSoftInputMode="adjustResize"` no longer resizes the window for the
 * IME. Worse, while the window's default `decorFitsSystemWindows` is `true`,
 * the Activity content frame *consumes* the insets (including the IME) before
 * they can reach a child's inset listener - so padding based on those insets
 * silently reads 0 and nothing moves.
 *
 * This helper switches the window to `decorFitsSystemWindows = false` so the
 * raw insets flow to [this] view, then pads:
 *   - top    by the status-bar / display-cutout inset, and
 *   - bottom by whichever is larger, the IME or the navigation bar.
 *
 * When [toolbar] is given, the status-bar inset goes to the toolbar instead of
 * the root: the toolbar grows underneath the status bar so its colour fills that
 * strip (white status icons stay readable), rather than leaving a band of page
 * background above it.
 *
 * Call once, after `setContentView`, on the ROOT view of the screen, and don't
 * add a hard-coded `layout_marginTop="24dp"` to the toolbar - the real inset
 * replaces it.
 */
fun View.applyImeAndSystemBarInsets(toolbar: View? = null) {
    (context as? Activity)?.let { activity ->
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        if (toolbar != null) {
            WindowCompat.getInsetsController(activity.window, this).isAppearanceLightStatusBars = false
        }
    }
    val top0 = paddingTop
    val bottom0 = paddingBottom
    val toolbarTop0 = toolbar?.paddingTop ?: 0
    val toolbarHeight0 = toolbar?.layoutParams?.height ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        if (toolbar != null) {
            toolbar.updatePadding(top = toolbarTop0 + bars.top)
            if (toolbarHeight0 > 0) toolbar.updateLayoutParams { height = toolbarHeight0 + bars.top }
            v.updatePadding(bottom = bottom0 + maxOf(imeBottom, bars.bottom))
        } else {
            v.updatePadding(
                top = top0 + bars.top,
                bottom = bottom0 + maxOf(imeBottom, bars.bottom)
            )
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
