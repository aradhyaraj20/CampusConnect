package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.common.ChangePasswordDialog
import com.example.campusconnect.core.common.Result
import com.example.campusconnect.core.common.RoleRouter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.data.repository.AuthRepository
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets()

        val userDao = AppDatabase.getDatabase(this).userDao()
        val sessionManager = SessionManager.getInstance(this)
        authRepository = AuthRepository(userDao, sessionManager)

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            // Taken exactly as typed: trimming would silently change a password with spaces.
            val password = binding.passwordEditText.text.toString()

            binding.emailLayout.error = if (email.isEmpty()) getString(R.string.error_email_required) else null
            binding.passwordLayout.error = if (password.isEmpty()) getString(R.string.error_password_required) else null

            if (email.isNotEmpty() && password.isNotEmpty()) {
                setLoading(true)
                lifecycleScope.launch {
                    val result = authRepository.login(email, password)
                    setLoading(false)
                    when (result) {
                        is Result.Success -> {
                            val user = result.data
                            if (user.must_change_password) {
                                // The first password came from the build; it must be replaced before going in.
                                ChangePasswordDialog.show(this@LoginActivity, knownCurrent = password, userId = user.id) {
                                    authRepository.startSession(user)
                                    RoleRouter.routeToDashboard(this@LoginActivity, user.role)
                                    finish()
                                }
                            } else {
                                RoleRouter.routeToDashboard(this@LoginActivity, user.role)
                                finish()
                            }
                        }
                        is Result.Error -> {
                            binding.passwordLayout.error = result.message ?: getString(R.string.login_failed)
                        }
                        else -> {}
                    }
                }
            }
        }

        binding.emailEditText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.emailLayout.error = null }
        binding.passwordEditText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.passwordLayout.error = null }

        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
    }
}
