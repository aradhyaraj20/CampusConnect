package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
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
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.edit_profile)

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
        val bio = binding.bioEditText.text.toString().trim()
        val tags = binding.interestsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

        binding.bioLayout.error = null
        binding.interestsLayout.error = null
        if (bio.length > MAX_BIO) {
            binding.bioLayout.error = getString(R.string.profile_err_bio)
            return
        }
        if (tags.size > MAX_INTERESTS || tags.any { it.length > MAX_INTEREST_LENGTH }) {
            binding.interestsLayout.error = getString(R.string.profile_err_interests)
            return
        }
        val uniqueTags = tags.distinctBy { it.lowercase() }

        binding.saveButton.isEnabled = false
        binding.savingProgress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@EditProfileActivity)
            if (db.userDao().getUserById(currentUserId) == null) {
                finish()
                return@launch
            }
            // Only bio and interests change, so a stale copy can't overwrite role, status or karma.
            db.userDao().updateProfile(
                currentUserId,
                bio.ifEmpty { null },
                uniqueTags.joinToString(", ").ifEmpty { null }
            )

            // Add interests to user_interests table for better querying/weighting
            uniqueTags.forEach { tag ->
                db.userDao().insertInterest(
                    com.example.campusconnect.models.UserInterest(
                        user_id = currentUserId,
                        interest_tag = tag.lowercase()
                    )
                )
            }

            Toast.makeText(this@EditProfileActivity, R.string.profile_updated, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private companion object {
        const val MAX_BIO = 300
        const val MAX_INTERESTS = 10
        const val MAX_INTEREST_LENGTH = 30
    }
}
