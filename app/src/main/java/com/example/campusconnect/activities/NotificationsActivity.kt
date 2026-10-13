package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.NotificationAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.network.AppConnectivity
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityNotificationsBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationAdapter
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.cd_notifications)

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        val db = AppDatabase.getDatabase(this)

        if (AppConnectivity.isOffline(this)) {
            binding.offlineBannerInclude.offlineBanner.visibility = View.VISIBLE
        }

        adapter = NotificationAdapter(emptyList()) { notification ->
            lifecycleScope.launch {
                db.notificationDao().insertNotification(notification.copy(is_read = true))
                notification.event_id?.let { id ->
                    val intent = Intent(this@NotificationsActivity, EventDetailActivity::class.java)
                    intent.putExtra("EVENT_ID", id)
                    startActivity(intent)
                }
            }
        }
        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            db.notificationDao().getNotificationsForUser(currentUserId).collectLatest { notifications ->
                if (notifications.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.notificationsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.notificationsRecyclerView.visibility = View.VISIBLE
                    adapter.updateNotifications(notifications)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Seen means read: clears the Home badge when the user leaves this screen.
        val db = AppDatabase.getDatabase(this)
        val userId = currentUserId
        lifecycleScope.launch(NonCancellable) { db.notificationDao().markAllRead(userId) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
