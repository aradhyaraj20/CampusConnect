package com.example.campusconnect.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.adapters.MarketplaceAdapter
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMarketplaceBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MarketplaceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMarketplaceBinding
    private lateinit var adapter: MarketplaceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarketplaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Marketplace"

        adapter = MarketplaceAdapter(emptyList()) { item ->
            // Handle click
        }

        binding.marketplaceRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.marketplaceRecyclerView.adapter = adapter

        binding.scrollUpFab.setOnClickListener {
            binding.marketplaceRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.marketplaceRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MarketplaceActivity).marketplaceDao().getAllItems().collectLatest { items ->
                adapter.updateItems(items)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
