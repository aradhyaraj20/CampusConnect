package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityEditProfileBinding
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Profile"

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        loadCurrentData()

        binding.saveButton.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadCurrentData() {
        lifecycleScope.launch {
            val user = AppDatabase.getDatabase(this@EditProfileActivity).userDao().getUserById(currentUserId)
            user?.let {
                binding.bioEditText.setText(it.bio)
                binding.interestsEditText.setText(it.interests)
            }
        }
    }

    private fun saveProfile() {
        val bio = binding.bioEditText.text.toString()
        val interests = binding.interestsEditText.text.toString()

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@EditProfileActivity)
            val user = db.userDao().getUserById(currentUserId)
            user?.let {
                val updatedUser = it.copy(bio = bio, interests = interests)
                db.userDao().insertUser(updatedUser)
                
                // Add interests to user_interests table for better querying/weighting
                interests.split(",").forEach { tag ->
                    val cleanTag = tag.trim().lowercase()
                    if (cleanTag.isNotEmpty()) {
                        db.userDao().insertInterest(com.example.campusconnect.models.UserInterest(
                            user_id = currentUserId,
                            interest_tag = cleanTag
                        ))
                    }
                }

                Toast.makeText(this@EditProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
