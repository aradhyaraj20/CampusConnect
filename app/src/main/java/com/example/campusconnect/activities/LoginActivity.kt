package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    when (val result = authRepository.login(email, password)) {
                        is Result.Success -> {
                            RoleRouter.routeToDashboard(this@LoginActivity, result.data.role)
                            finish()
                        }
                        is Result.Error -> {
                            Toast.makeText(this@LoginActivity, result.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
