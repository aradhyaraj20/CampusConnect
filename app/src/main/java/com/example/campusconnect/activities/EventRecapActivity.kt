package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.campusconnect.adapters.RecapPhotoAdapter
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityEventRecapBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EventRecapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventRecapBinding
    private lateinit var adapter: RecapPhotoAdapter
    private var eventId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Event Memories"

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1) {
            finish()
            return
        }

        adapter = RecapPhotoAdapter(emptyList())
        binding.recapRecyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recapRecyclerView.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@EventRecapActivity).eventDao().getRecapPhotos(eventId).collectLatest { photos ->
                adapter.updatePhotos(photos)
            }
        }

        binding.addPhotoFab.setOnClickListener {
            Toast.makeText(this, "Upload feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
