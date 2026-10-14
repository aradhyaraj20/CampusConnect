package com.example.campusconnect.core.common

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.campusconnect.R
import com.google.android.material.button.MaterialButton

/** Previous / "Page x of y" / Next strip. Hides itself when there is only one page. */
class PagerBar(context: Context) : LinearLayout(context) {
    private val prev = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle)
    private val next = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle)
    private val label = TextView(context)

    var onPrev: () -> Unit = {}
    var onNext: () -> Unit = {}

    init {
        id = View.generateViewId()
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val pad = (8 * resources.displayMetrics.density).toInt()
        // Right side is padded so the floating AI button never covers "Next".
        setPadding(pad, pad / 2, (80 * resources.displayMetrics.density).toInt(), pad / 2)
        prev.text = context.getString(R.string.pager_prev)
        next.text = context.getString(R.string.pager_next)
        prev.minimumHeight = (48 * resources.displayMetrics.density).toInt()
        next.minimumHeight = prev.minimumHeight
        label.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        label.textSize = 14f
        label.gravity = Gravity.CENTER
        addView(prev)
        addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(next)
        prev.setOnClickListener { onPrev() }
        next.setOnClickListener { onNext() }
        visibility = GONE
    }

    /** [page] is zero-based. */
    fun show(page: Int, pages: Int) {
        visibility = if (pages > 1) VISIBLE else GONE
        label.text = context.getString(R.string.pager_label, page + 1, pages.coerceAtLeast(1))
        prev.isEnabled = page > 0
        next.isEnabled = page < pages - 1
    }

    companion object {
        /** Inserts a bar directly below [list] (constraint-aware for ConstraintLayout parents). */
        fun attachBelow(list: RecyclerView): PagerBar {
            val bar = PagerBar(list.context)
            when (val parent = list.parent) {
                is ConstraintLayout -> {
                    bar.layoutParams = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
                    parent.addView(bar)
                    ConstraintSet().apply {
                        clone(parent)
                        connect(bar.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        connect(bar.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        connect(bar.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        connect(list.id, ConstraintSet.BOTTOM, bar.id, ConstraintSet.TOP)
                        applyTo(parent)
                    }
                }
                is ViewGroup -> {
                    bar.layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    parent.addView(bar, parent.indexOfChild(list) + 1)
                }
            }
            return bar
        }
    }
}

/**
 * Client-side pagination: hand it the full list, it shows [pageSize] items per page
 * through [onPage] and drives the [PagerBar].
 */
class Pager<T>(
    private val list: RecyclerView,
    private val pageSize: Int = 5,
    private val onPage: (List<T>) -> Unit
) {
    private val bar = PagerBar.attachBelow(list)
    private var all: List<T> = emptyList()
    private var page = 0

    init {
        bar.onPrev = { if (page > 0) { page--; render(scroll = true) } }
        bar.onNext = { if (page < pages() - 1) { page++; render(scroll = true) } }
    }

    private fun pages() = ((all.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    fun submit(items: List<T>) {
        all = items
        page = page.coerceIn(0, pages() - 1)
        render(scroll = false)
    }

    private fun render(scroll: Boolean) {
        onPage(all.drop(page * pageSize).take(pageSize))
        bar.show(page, pages())
        if (scroll) list.scrollToPosition(0)
    }

    /** Back to page 1 - call when the filter/query changes. */
    fun reset() { page = 0 }
}
