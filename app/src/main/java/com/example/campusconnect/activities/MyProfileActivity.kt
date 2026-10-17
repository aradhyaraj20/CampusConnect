package com.example.campusconnect.activities

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.activities.admin.AdminAdsActivity
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityMyProfileBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Previously, tapping "Profile" in the Organizer/Admin bottom nav jumped
 * straight into a bare edit form. This gives them the same kind of profile
 * view students get - avatar, bio, interests - plus role-relevant stats
 * (events created/registrations for organizers, an ads shortcut for admins)
 * instead of platform-wide numbers that belong on the Dashboard/Analytics
 * screens.
 */
class MyProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyProfileBinding
    private var currentUserId: Int = -1
    private var currentRole: String = "STUDENT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.my_profile)

        val session = SessionManager.getInstance(this)
        currentUserId = session.getCurrentUserId()
        currentRole = session.getCurrentUserRole()

        binding.roleBadge.text = currentRole
        binding.manageAdsShortcut.visibility =
            if (currentRole == "ADMIN") android.view.View.VISIBLE else android.view.View.GONE

        if (currentRole == "ADMIN") {
            binding.statOneLabel.setText(R.string.stat_total_users)
            binding.statTwoLabel.setText(R.string.stat_total_events)
        } else {
            binding.statOneLabel.setText(R.string.stat_events_created)
            binding.statTwoLabel.setText(R.string.stat_registrations)
        }

        loadProfile()
        loadStats()

        binding.profileImage.setOnClickListener {
            startActivity(Intent(this, AvatarPickerActivity::class.java))
        }
        binding.editAvatarIcon.setOnClickListener {
            startActivity(Intent(this, AvatarPickerActivity::class.java))
        }
        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.manageAdsShortcut.setOnClickListener {
            startActivity(Intent(this, AdminAdsActivity::class.java))
        }
        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout) { _, _ ->
                    session.clearSession()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    private fun loadProfile() {
        val session = SessionManager.getInstance(this)
        binding.userName.text = session.getCurrentUserName()
        binding.userEmail.text = session.getCurrentUserEmail()

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MyProfileActivity).userDao().getUserByIdFlow(currentUserId)
                .collectLatest { user ->
                    user?.let {
                        binding.userBio.text = it.bio ?: "No bio yet. Tap edit to add one!"

                        binding.interestsChipGroup.removeAllViews()
                        it.interests?.split(",")?.forEach { interest ->
                            val cleanInterest = interest.trim()
                            if (cleanInterest.isNotEmpty()) {
                                val chip = Chip(this@MyProfileActivity)
                                chip.text = cleanInterest
                                binding.interestsChipGroup.addView(chip)
                            }
                        }

                        if (it.avatar_uri != null) {
                            binding.profileImage.load(it.avatar_uri) {
                                crossfade(true)
                                placeholder(R.drawable.ic_user_placeholder)
                                error(R.drawable.ic_user_placeholder)
                            }
                        } else {
                            binding.profileImage.setImageResource(R.drawable.ic_user_placeholder)
                        }
                    }
                }
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MyProfileActivity)
            if (currentRole == "ADMIN") {
                binding.statOneValue.text = db.userDao().getUsersPageCount("", "ALL").toString()
                binding.statTwoValue.text = db.eventDao().getEventCount().toString()
            } else {
                binding.statOneValue.text = db.eventDao().getEventCountByCreator(currentUserId).toString()
                binding.statTwoValue.text = db.rsvpDao().getRegistrationCountForOrganizer(currentUserId).toString()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
