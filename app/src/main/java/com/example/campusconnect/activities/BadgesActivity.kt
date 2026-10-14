package com.example.campusconnect.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.BadgeAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityBadgesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BadgesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBadgesBinding
    private lateinit var adapter: BadgeAdapter
    private lateinit var pager: com.example.campusconnect.core.common.Pager<com.example.campusconnect.models.Badge>
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBadgesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.my_badges)

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        adapter = BadgeAdapter(emptyList())
        binding.badgesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.badgesRecyclerView.adapter = adapter
        pager = com.example.campusconnect.core.common.Pager(binding.badgesRecyclerView) { adapter.updateBadges(it) }

        binding.scrollUpFab.setOnClickListener {
            binding.badgesRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.badgesRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@BadgesActivity).badgeDao().getUserBadges(currentUserId).collectLatest { badges ->
                pager.submit(badges)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
