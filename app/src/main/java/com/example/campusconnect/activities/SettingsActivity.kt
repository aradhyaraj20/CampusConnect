package com.example.campusconnect.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.campusconnect.BuildConfig
import com.example.campusconnect.R
import com.example.campusconnect.activities.admin.AdminAiConfigActivity
import com.example.campusconnect.core.security.CryptoUtils
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySettingsBinding
import com.example.campusconnect.databinding.DialogChangePasswordBinding
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
            AlertDialog.Builder(this)
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

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)

        dialogBinding.newPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                showStrength(dialogBinding, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_change_password)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pwd_update, null)
            .create()

        dialog.show()
        // Set the click listener after show() so validation failures don't dismiss it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            attemptPasswordChange(dialogBinding, dialog)
        }
    }

    private fun scorePassword(pwd: String): Int {
        var score = 0
        if (pwd.length >= 8) score++
        if (pwd.length >= 12) score++
        if (pwd.any { it.isDigit() }) score++
        if (pwd.any { it.isLetter() && it.isUpperCase() } && pwd.any { it.isLowerCase() }) score++
        if (pwd.any { !it.isLetterOrDigit() }) score++
        return score
    }

    private fun showStrength(b: DialogChangePasswordBinding, pwd: String) {
        if (pwd.isEmpty()) {
            b.strengthText.text = ""
            return
        }
        val score = scorePassword(pwd)
        val (label, color) = when {
            score <= 2 -> getString(R.string.pwd_weak) to R.color.error
            score == 3 -> getString(R.string.pwd_fair) to R.color.warning
            score == 4 -> getString(R.string.pwd_good) to R.color.success
            else -> getString(R.string.pwd_strong) to R.color.success
        }
        b.strengthText.text = label
        b.strengthText.setTextColor(getColor(color))
    }

    private fun attemptPasswordChange(b: DialogChangePasswordBinding, dialog: AlertDialog) {
        val current = b.currentPasswordInput.text?.toString().orEmpty()
        val new = b.newPasswordInput.text?.toString().orEmpty()
        val confirm = b.confirmPasswordInput.text?.toString().orEmpty()

        b.currentPasswordLayout.error = null
        b.newPasswordLayout.error = null
        b.confirmPasswordLayout.error = null

        when {
            current.isBlank() -> {
                b.currentPasswordLayout.error = getString(R.string.pwd_err_current_required); return
            }
            new.length < 8 -> {
                b.newPasswordLayout.error = getString(R.string.pwd_err_too_short); return
            }
            scorePassword(new) < 3 -> {
                b.newPasswordLayout.error = getString(R.string.pwd_err_weak); return
            }
            new == current -> {
                b.newPasswordLayout.error = getString(R.string.pwd_err_same); return
            }
            new != confirm -> {
                b.confirmPasswordLayout.error = getString(R.string.pwd_err_mismatch); return
            }
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@SettingsActivity).userDao()
            val userId = session.getCurrentUserId()
            val user = withContext(Dispatchers.IO) { dao.getUserById(userId) }

            if (user == null) {
                Toast.makeText(this@SettingsActivity, R.string.pwd_err_no_user, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Verify the current password before allowing a change - otherwise anyone
            // with a borrowed unlocked phone could take over the account.
            if (!CryptoUtils.verifyPassword(current, user.password_hash)) {
                b.currentPasswordLayout.error = getString(R.string.pwd_err_current_wrong)
                return@launch
            }

            withContext(Dispatchers.IO) {
                dao.updatePassword(userId, CryptoUtils.hashPassword(new))
            }
            dialog.dismiss()
            Toast.makeText(this@SettingsActivity, R.string.pwd_updated, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- Appearance ----------------

    private fun bindAppearance() {
        val followSystem = prefs.getBoolean("follow_system_theme", false)
        binding.followSystemThemeSwitch.isChecked = followSystem
        binding.darkModeSwitch.isChecked = session.isDarkModeEnabled()
        binding.darkModeSwitch.isEnabled = !followSystem

        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            session.setDarkModeEnabled(isChecked)
            applyTheme()
        }
        binding.followSystemThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("follow_system_theme", isChecked).apply()
            binding.darkModeSwitch.isEnabled = !isChecked
            applyTheme()
        }
    }

    private fun applyTheme() {
        val mode = when {
            prefs.getBoolean("follow_system_theme", false) ->
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            session.isDarkModeEnabled() -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

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
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_cache)
                .setMessage(R.string.settings_clear_cache_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_clear) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }
                        updateCacheSize()
                        Toast.makeText(this@SettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
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
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.helpRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_help)
                .setMessage(R.string.help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.privacyRow.setOnClickListener {
            AlertDialog.Builder(this)
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
