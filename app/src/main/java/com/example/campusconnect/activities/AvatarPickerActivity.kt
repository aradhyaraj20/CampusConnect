package com.example.campusconnect.activities

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.campusconnect.adapters.AvatarAdapter
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAvatarPickerBinding
import kotlinx.coroutines.launch

class AvatarPickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAvatarPickerBinding
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvatarPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Choose Your Avatar"

        val prefs = getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt("user_id", -1)

        val avatarSeeds = listOf(
            "Felix", "Aneka", "Buddy", "Max", "Jack", "Spooky", 
            "Milo", "Ginger", "Lilly", "Kiki", "Sassy", "Cookie",
            "Muffin", "Buster", "Jasper", "Oliver", "Simba", "Leo"
        )
        
        val adventurerUrls = avatarSeeds.map { "https://api.dicebear.com/7.x/adventurer/png?seed=$it" }
        val avataaarsUrls = avatarSeeds.map { "https://api.dicebear.com/7.x/avataaars/png?seed=$it" }
        val allUrls = adventurerUrls + avataaarsUrls

        val adapter = AvatarAdapter(allUrls) { selectedUrl ->
            // Just selection in adapter, no auto-close
        }

        binding.avatarRecyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.avatarRecyclerView.adapter = adapter

        binding.confirmButton.setOnClickListener {
            val selectedUrl = adapter.getSelectedUrl()
            if (selectedUrl != null) {
                updateProfileAvatar(selectedUrl)
            } else {
                Toast.makeText(this, "Please select an avatar first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProfileAvatar(url: String) {
        if (currentUserId == -1) return

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@AvatarPickerActivity)
            db.userDao().updateAvatar(currentUserId, url)
            
            // Also update SharedPreferences for consistency if needed, but ProfileFragment loads from DB
            Toast.makeText(this@AvatarPickerActivity, "Avatar updated!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
