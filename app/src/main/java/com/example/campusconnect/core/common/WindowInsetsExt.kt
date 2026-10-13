package com.example.campusconnect.core.common

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
 * Call once, after `setContentView`, on the ROOT view of the screen. Because
 * the top status-bar spacing now comes from the real inset, remove any
 * hard-coded `android:layout_marginTop="24dp"` fake status-bar spacing from the
 * toolbar in that screen's layout, otherwise the top gap is doubled.
 */
fun View.applyImeAndSystemBarInsets() {
    (context as? Activity)?.let { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
    val top0 = paddingTop
    val bottom0 = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        v.updatePadding(
            top = top0 + bars.top,
            bottom = bottom0 + maxOf(imeBottom, bars.bottom)
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
