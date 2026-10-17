package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.campusconnect.R
import com.example.campusconnect.core.common.NotificationPermission
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMainBinding
import com.example.campusconnect.utils.SeedData
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val notificationPermission = NotificationPermission(this)
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar, hasBottomNav = true)
        notificationPermission.request()
        setSupportActionBar(binding.toolbar)
        binding.toolbar.overflowIcon?.setTint(
            com.google.android.material.color.MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorOnPrimary)
        )

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            SeedData.seedIfEmpty(db)
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.studyGroupsFragment, R.id.calendarFragment, R.id.profileFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)
        showUpcomingEventsBadge()

        // The Home screen ships its own full-bleed gradient header (Student
        // Portal design), so the shared toolbar is hidden there and shown on
        // every other destination.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // AI assistant FAB is hidden on Profile (settings/profile stay uncluttered).
            binding.aiAssistantFab.visibility =
                if (destination.id == R.id.profileFragment) android.view.View.GONE else android.view.View.VISIBLE
            if (destination.id == R.id.homeFragment) {
                binding.toolbar.visibility = android.view.View.GONE
                supportActionBar?.hide()
            } else {
                binding.toolbar.visibility = android.view.View.VISIBLE
                supportActionBar?.show()
            }
        }

        binding.notificationButton.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        binding.aiAssistantFab.setOnClickListener {
            startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_STUDENT)
            })
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(com.example.campusconnect.R.menu.toolbar_account_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return com.example.campusconnect.core.common.AccountMenuHelper
            .handleMenuItem(this, item.itemId) || super.onOptionsItemSelected(item)
    }

    /** Number badge on the My Events tab: upcoming events this student has a confirmed seat for. */
    private fun showUpcomingEventsBadge() {
        val userId = SessionManager.getInstance(this).getCurrentUserId()
        if (userId <= 0) return
        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MainActivity).eventDao()
                .getUpcomingGoingEvents(userId, startOfToday)
                .collectLatest { events ->
                    if (events.isEmpty()) {
                        binding.bottomNav.removeBadge(R.id.calendarFragment)
                    } else {
                        binding.bottomNav.getOrCreateBadge(R.id.calendarFragment).apply {
                            number = events.size
                            maxCharacterCount = 2
                            setContentDescriptionQuantityStringsResource(R.plurals.cd_upcoming_events_badge)
                        }
                    }
                }
        }
    }
}
