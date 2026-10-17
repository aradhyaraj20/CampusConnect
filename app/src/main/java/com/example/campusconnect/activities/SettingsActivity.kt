package com.example.campusconnect.activities

import com.example.campusconnect.core.common.showMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.campusconnect.BuildConfig
import com.example.campusconnect.R
import com.example.campusconnect.core.common.ChangePasswordDialog
import com.example.campusconnect.core.common.ThemeMode
import com.example.campusconnect.activities.admin.AdminAiConfigActivity
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var session: SessionManager
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        session = SessionManager.getInstance(this)
        prefs = getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)

        bindAppearance()
        bindNotifications()
        bindData()
        bindAbout()
        bindAccountActions()
        bindAdmin()
    }

    override fun onResume() {
        super.onResume()
        // Re-read on resume so a photo or name changed elsewhere shows immediately.
        renderProfileHeader()
    }

    // ---------------- Account ----------------

    private fun renderProfileHeader() {
        binding.profileName.text = session.getCurrentUserName()
        binding.profileEmail.text = session.getCurrentUserEmail()
        binding.profileRoleChip.text = session.getCurrentUserRole()

        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@SettingsActivity).userDao()
                    .getUserById(session.getCurrentUserId())
            }
            val avatar = user?.avatar_uri
            if (!avatar.isNullOrBlank()) {
                binding.profileAvatar.load(avatar) {
                    crossfade(true)
                    placeholder(R.drawable.ic_user_placeholder)
                    error(R.drawable.ic_user_placeholder)
                }
                binding.changePhotoSubtitle.setText(R.string.settings_change_photo_set)
            } else {
                binding.profileAvatar.setImageResource(R.drawable.ic_user_placeholder)
                binding.changePhotoSubtitle.setText(R.string.settings_change_photo_sub)
            }
        }
    }

    private fun bindAccountActions() {
        binding.changePhotoRow.setOnClickListener {
            startActivity(Intent(this, AvatarPickerActivity::class.java))
        }
        binding.profileAvatar.setOnClickListener {
            startActivity(Intent(this, AvatarPickerActivity::class.java))
        }
        binding.editProfileRow.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        binding.changePasswordRow.setOnClickListener { showChangePasswordDialog() }

        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout) { _, _ ->
                    // Clear only the session - not every preference, which would also
                    // wipe the user's theme and notification choices.
                    session.clearSession()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    // ---------------- Admin ----------------

    /**
     * The AI Configuration entry is admin-only. Gating the whole section (not just
     * hiding a row visually) keeps the Gemini key management restricted to admins.
     */
    private fun bindAdmin() {
        val isAdmin = session.getCurrentUserRole() == "ADMIN"
        binding.adminSection.visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
        if (isAdmin) {
            binding.aiConfigRow.setOnClickListener {
                startActivity(Intent(this, AdminAiConfigActivity::class.java))
            }
        }
    }

    private fun showChangePasswordDialog() = ChangePasswordDialog.show(this)

    // ---------------- Appearance ----------------

    private fun bindAppearance() {
        val followSystem = ThemeMode.followsSystem(this)
        binding.followSystemThemeSwitch.isChecked = followSystem
        binding.darkModeSwitch.isChecked = session.isDarkModeEnabled()
        binding.darkModeSwitch.isEnabled = !followSystem

        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            session.setDarkModeEnabled(isChecked)
            applyTheme()
        }
        binding.followSystemThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemeMode.setFollowsSystem(this, isChecked)
            binding.darkModeSwitch.isEnabled = !isChecked
            applyTheme()
        }
    }

    private fun applyTheme() = ThemeMode.apply(this)

    // ---------------- Notifications ----------------

    private fun bindNotifications() {
        binding.eventRemindersSwitch.isChecked = prefs.getBoolean("notify_event_reminders", true)
        binding.chatNotificationsSwitch.isChecked = prefs.getBoolean("notify_chat", true)
        binding.announcementsSwitch.isChecked = prefs.getBoolean("notify_announcements", true)

        binding.eventRemindersSwitch.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("notify_event_reminders", v).apply()
        }
        binding.chatNotificationsSwitch.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("notify_chat", v).apply()
        }
        binding.announcementsSwitch.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("notify_announcements", v).apply()
        }
    }

    // ---------------- Data ----------------

    private fun bindData() {
        binding.offlineModeSwitch.isChecked = session.isOfflineModeEnabled()
        binding.offlineModeSwitch.setOnCheckedChangeListener { _, v ->
            session.setOfflineModeEnabled(v)
        }

        updateCacheSize()
        binding.clearCacheRow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_cache)
                .setMessage(R.string.settings_clear_cache_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_clear) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }
                        updateCacheSize()
                        showMessage(R.string.settings_cache_cleared)
                    }
                }
                .show()
        }
    }

    private fun updateCacheSize() {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
                    .getOrDefault(0L)
            }
            val mb = bytes / (1024.0 * 1024.0)
            binding.cacheSizeText.text = getString(R.string.settings_cache_size, mb)
        }
    }

    // ---------------- About ----------------

    private fun bindAbout() {
        binding.versionText.text = getString(
            R.string.settings_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        binding.aboutRow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.helpRow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_help)
                .setMessage(R.string.help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.privacyRow.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_privacy)
                .setMessage(R.string.privacy_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
