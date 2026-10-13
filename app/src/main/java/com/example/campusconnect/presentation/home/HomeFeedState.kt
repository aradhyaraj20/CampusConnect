package com.example.campusconnect.presentation.home

data class HomeFeedVisibility(
    val skeleton: Boolean,
    val empty: Boolean,
    val list: Boolean
)

object HomeFeedState {
    fun visibility(hasLoaded: Boolean, eventCount: Int): HomeFeedVisibility {
        val skeleton = !hasLoaded
        val empty = hasLoaded && eventCount == 0
        return HomeFeedVisibility(
            skeleton = skeleton,
            empty = empty,
            list = hasLoaded && eventCount > 0
        )
    }
}
