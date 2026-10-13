package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.BuildConfig
import com.example.campusconnect.core.common.Result
import com.example.campusconnect.core.common.RoleRouter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
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
        supportActionBar?.title = "Register"

        val userDao = AppDatabase.getDatabase(this).userDao()
        val sessionManager = SessionManager.getInstance(this)
        authRepository = AuthRepository(userDao, sessionManager, BuildConfig.ORGANIZER_INVITE_CODE)

        // Organizer self-signup needs an invite code; hide the option entirely if none is configured.
        if (!authRepository.organizerSignupEnabled) {
            binding.roleLabel.visibility = android.view.View.GONE
            binding.roleRadioGroup.visibility = android.view.View.GONE
        }

        fun updateCourseSectionVisibility() {
            binding.inviteCodeLayout.visibility =
                if (binding.roleOrganizerRadio.isChecked) android.view.View.VISIBLE else android.view.View.GONE
            // Organizers run/manage courses rather than being enrolled in one,
            // so the course-scoped leaderboard fields only apply to students.
            binding.courseSection.visibility =
                if (binding.roleOrganizerRadio.isChecked) android.view.View.GONE else android.view.View.VISIBLE
        }
        updateCourseSectionVisibility()
        binding.roleRadioGroup.setOnCheckedChangeListener { _, _ -> updateCourseSectionVisibility() }

        binding.registerButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            val role = if (binding.roleOrganizerRadio.isChecked) "ORGANIZER" else "STUDENT"
            val course = binding.courseEditText.text.toString().trim()
            val courseDuration = binding.courseDurationEditText.text.toString().trim().toIntOrNull()

            val isStudent = role == "STUDENT"
            val courseFieldsValid = !isStudent || (course.isNotEmpty() && courseDuration != null && courseDuration > 0)

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && courseFieldsValid) {
                lifecycleScope.launch {
                    when (
                        val result = authRepository.register(
                            name, email, password, role,
                            course = if (isStudent) course else null,
                            courseDurationMonths = if (isStudent) courseDuration else null,
                            inviteCode = binding.inviteCodeEditText.text.toString()
                        )
                    ) {
                        is Result.Success -> {
                            Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                            RoleRouter.routeToDashboard(this@RegisterActivity, result.data.role)
                            finish()
                        }
                        is Result.Error -> {
                            Toast.makeText(this@RegisterActivity, result.message ?: "Registration failed", Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
            } else if (!courseFieldsValid) {
                Toast.makeText(this, "Please enter your course and its duration (months)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.loginTextView.setOnClickListener {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
