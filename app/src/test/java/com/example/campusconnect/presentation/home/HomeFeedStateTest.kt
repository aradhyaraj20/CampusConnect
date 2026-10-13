package com.example.campusconnect.presentation.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedStateTest {

    @Test
    fun beforeLoad_showsSkeletonOnly() {
        val vis = HomeFeedState.visibility(hasLoaded = false, eventCount = 0)
        assertTrue(vis.skeleton)
        assertFalse(vis.empty)
        assertFalse(vis.list)
    }

    @Test
    fun loadedEmpty_showsEmptyState() {
        val vis = HomeFeedState.visibility(hasLoaded = true, eventCount = 0)
        assertFalse(vis.skeleton)
        assertTrue(vis.empty)
        assertFalse(vis.list)
    }

    @Test
    fun loadedWithEvents_showsList() {
        val vis = HomeFeedState.visibility(hasLoaded = true, eventCount = 4)
        assertFalse(vis.skeleton)
        assertFalse(vis.empty)
        assertTrue(vis.list)
    }
}
