package com.example.campusconnect.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.adapters.SearchResultAdapter
import com.example.campusconnect.core.network.AppConnectivity
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySearchBinding
import com.example.campusconnect.repository.SearchRepository
import com.example.campusconnect.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: SearchResultAdapter
    private var currentUserId: Int = -1
    private var hasSearched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt("user_id", -1)

        val db = AppDatabase.getDatabase(this)
        val searchRepo = SearchRepository(db.eventDao(), db.userDao(), db.rsvpDao())

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(searchRepo) as T
            }
        })[SearchViewModel::class.java]

        adapter = SearchResultAdapter(emptyList()) { result ->
            val intent = Intent(this, EventDetailActivity::class.java)
            intent.putExtra("EVENT_ID", result.event.id)
            startActivity(intent)
        }

        binding.resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.resultsRecyclerView.adapter = adapter

        if (AppConnectivity.isOffline(this)) {
            binding.offlineBannerInclude.offlineBanner.visibility = View.VISIBLE
        }
        binding.askAiFromSearchButton.setOnClickListener {
            startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_STUDENT)
            })
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.performSearch(s.toString(), currentUserId)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        intent.getStringExtra("PREFILL_QUERY")?.let { prefill ->
            binding.searchEditText.setText(prefill)
            viewModel.performSearch(prefill, currentUserId)
        }

        lifecycleScope.launch {
            viewModel.isSearching.collectLatest { searching ->
                binding.searchProgress.visibility = if (searching) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.hasSearched.collectLatest { searched ->
                hasSearched = searched
                updateEmptyState()
            }
        }

        lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                adapter.updateResults(results)
                updateEmptyState()
            }
        }

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@SearchActivity).userDao().getRecentSearch(currentUserId).collectLatest { history ->
                if (history.isNotEmpty()) {
                    binding.historyHeader.visibility = View.VISIBLE
                    binding.historyChipGroup.removeAllViews()
                    history.forEach { item ->
                        val chip = Chip(this@SearchActivity)
                        chip.text = item.query
                        chip.setOnClickListener {
                            binding.searchEditText.setText(item.query)
                            viewModel.performSearch(item.query, currentUserId)
                        }
                        binding.historyChipGroup.addView(chip)
                    }
                } else {
                    binding.historyHeader.visibility = View.GONE
                }
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = hasSearched && adapter.itemCount == 0
        binding.emptyResultsState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.resultsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
