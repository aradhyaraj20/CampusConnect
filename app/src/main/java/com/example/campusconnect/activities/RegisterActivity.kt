package com.example.campusconnect.activities

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.common.Result
import com.example.campusconnect.core.common.RoleRouter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.common.showMessage
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.data.repository.AuthRepository
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.register)

        val userDao = AppDatabase.getDatabase(this).userDao()
        val sessionManager = SessionManager.getInstance(this)
        authRepository = AuthRepository(userDao, sessionManager)

        // Student or organizer: organizers skip the course fields, which only drive the
        // course-scoped leaderboard and the post-course account cleanup.
        fun updateRoleSection() {
            val organizer = binding.roleOrganizerButton.isChecked
            binding.courseSection.visibility = if (organizer) View.GONE else View.VISIBLE
            binding.roleHint.setText(if (organizer) R.string.reg_role_hint_organizer else R.string.reg_role_hint_student)
        }
        updateRoleSection()
        binding.roleToggleGroup.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) updateRoleSection() }

        binding.registerButton.setOnClickListener { submit() }

        binding.loginTextView.setOnClickListener {
            finish()
        }
    }

    /** Validates every field and shows each problem on the field itself, not in a toast. */
    private fun submit() {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        // Passwords are taken exactly as typed (no trimming), matching login.
        val password = binding.passwordEditText.text.toString()
        val confirm = binding.confirmPasswordEditText.text.toString()
        val role = if (binding.roleOrganizerButton.isChecked) "ORGANIZER" else "STUDENT"
        val isStudent = role == "STUDENT"
        val course = binding.courseEditText.text.toString().trim()
        val courseDuration = binding.courseDurationEditText.text.toString().trim().toIntOrNull()

        val layouts = listOf(
            binding.nameLayout, binding.emailLayout, binding.passwordLayout, binding.confirmPasswordLayout,
            binding.courseLayout, binding.courseDurationLayout
        )
        layouts.forEach { it.error = null }

        val errors = mutableListOf<Pair<com.google.android.material.textfield.TextInputLayout, Int>>()
        if (name.isEmpty() || name.length > AuthRepository.MAX_NAME_LENGTH) errors += binding.nameLayout to R.string.reg_err_name
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) errors += binding.emailLayout to R.string.reg_err_email
        if (password.length < AuthRepository.MIN_PASSWORD_LENGTH) errors += binding.passwordLayout to R.string.pwd_err_too_short
        else if (password != confirm) errors += binding.confirmPasswordLayout to R.string.pwd_err_mismatch
        if (isStudent && course.isEmpty()) errors += binding.courseLayout to R.string.reg_err_course
        if (isStudent && (courseDuration == null || courseDuration !in 1..MAX_COURSE_MONTHS)) {
            errors += binding.courseDurationLayout to R.string.reg_err_duration
        }
        if (errors.isNotEmpty()) {
            errors.forEach { (layout, message) -> layout.error = getString(message, MAX_COURSE_MONTHS) }
            errors.first().first.requestFocus()
            return
        }

        binding.registerButton.isEnabled = false
        binding.registerProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = authRepository.register(
                name, email, password, role,
                course = if (isStudent) course else null,
                courseDurationMonths = if (isStudent) courseDuration else null
            )
            binding.registerButton.isEnabled = true
            binding.registerProgress.visibility = View.INVISIBLE
            when (result) {
                is Result.Success -> {
                    Toast.makeText(this@RegisterActivity, R.string.registration_successful, Toast.LENGTH_SHORT).show()
                    RoleRouter.routeToDashboard(this@RegisterActivity, result.data.role)
                    finish()
                }
                is Result.Error -> {
                    val message = result.message ?: getString(R.string.registration_failed)
                    // Put server-side validation messages on the field they belong to.
                    when {
                        message.contains("email", ignoreCase = true) -> binding.emailLayout.error = message
                        message.contains("Password", ignoreCase = true) -> binding.passwordLayout.error = message
                        message.contains("name", ignoreCase = true) -> binding.nameLayout.error = message
                        else -> showMessage(message)
                    }
                }
                else -> {}
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private companion object {
        const val MAX_COURSE_MONTHS = 96
    }
}
