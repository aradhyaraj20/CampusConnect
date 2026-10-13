package com.example.campusconnect.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMapBinding
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapBinding
    private var eventId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Campus Navigation"

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId != -1) {
            loadLocationInfo()
        }
    }

    private fun loadLocationInfo() {
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@MapActivity).eventDao().getEventById(eventId)
            event?.let {
                binding.locationLabel.text = it.location
                supportActionBar?.subtitle = it.title
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
