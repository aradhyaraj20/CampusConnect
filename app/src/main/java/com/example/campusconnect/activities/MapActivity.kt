package com.example.campusconnect.activities

import com.example.campusconnect.core.common.showMessage
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMapBinding
import kotlinx.coroutines.launch

/**
 * Event location. The app has no map SDK or API key (it is offline-first), so instead of a fake
 * map it hands the location to whatever maps app is installed, falling back to the browser.
 */
class MapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapBinding
    private var eventId: Int = -1
    private var location: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.campus_navigation)

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1) {
            finish()
            return
        }

        binding.openMapsButton.setOnClickListener {
            open(Uri.parse("geo:0,0?q=" + Uri.encode(location)), searchUrl())
        }
        binding.directionsButton.setOnClickListener {
            open(Uri.parse("google.navigation:q=" + Uri.encode(location)), directionsUrl())
        }
        binding.copyButton.setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.map_clip_label), location))
            showMessage(R.string.map_copied)
        }
        setButtonsEnabled(false)
        loadLocationInfo()
    }

    private fun loadLocationInfo() {
        binding.loadingProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@MapActivity).eventDao().getEventById(eventId)
            binding.loadingProgress.visibility = View.GONE
            if (event == null) {
                Toast.makeText(this@MapActivity, R.string.event_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            location = event.location.trim()
            supportActionBar?.subtitle = event.title
            binding.locationLabel.text = location.ifEmpty { getString(R.string.map_no_location) }
            binding.eventWhen.text = getString(R.string.date_time_format, event.date, event.time)
            setButtonsEnabled(location.isNotEmpty())
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.openMapsButton.isEnabled = enabled
        binding.directionsButton.isEnabled = enabled
        binding.copyButton.isEnabled = enabled
    }

    private fun searchUrl() = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(location))

    private fun directionsUrl() = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(location))

    /** Tries the maps app first, then the browser; tells the user if neither exists. */
    private fun open(primary: Uri, fallback: Uri) {
        for (uri in listOf(primary, fallback)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        showMessage(R.string.map_no_app)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
