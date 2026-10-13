package com.example.campusconnect.activities.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.ai.AIConfig
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.data.remote.GeminiAIDataSource
import com.example.campusconnect.databinding.ActivityAdminAiConfigBinding
import com.example.campusconnect.domain.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Admin-only screen for supplying the Gemini API key at runtime (spec sections 15 & 37).
 *
 * The key is written to private SharedPreferences via AIConfig - never to source, XML,
 * or strings.xml - and is only ever echoed back to the UI in masked form.
 */
class AdminAiConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAiConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAiConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ai_configuration)

        // Enforced at the destination, not just by hiding the entry point (spec section 3).
        if (SessionManager.getInstance(this).getCurrentUserRole() != Role.ADMIN.name) {
            Toast.makeText(this, R.string.admin_access_required, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        AIConfig.init(this)
        renderStatus()

        binding.modelFlash.isChecked = AIConfig.model != "gemini-2.5-flash-lite"
        binding.modelFlashLite.isChecked = AIConfig.model == "gemini-2.5-flash-lite"

        binding.saveKeyButton.setOnClickListener { saveKey() }
        binding.testConnectionButton.setOnClickListener { testConnection() }
        binding.clearKeyButton.setOnClickListener { confirmClear() }
    }

    private fun selectedModel(): String =
        if (binding.modelFlashLite.isChecked) "gemini-2.5-flash-lite" else "gemini-2.5-flash"

    private fun renderStatus() {
        val configured = AIConfig.isConfigured
        binding.statusTitle.text = getString(
            if (configured) R.string.ai_status_active else R.string.ai_status_not_configured
        )
        binding.statusDetail.text = when {
            !configured -> getString(R.string.ai_status_detail_none)
            AIConfig.isUsingRuntimeKey -> getString(R.string.ai_status_detail_runtime)
            else -> getString(R.string.ai_status_detail_build)
        }
        binding.activeKeyText.text = getString(R.string.ai_active_key_format, AIConfig.maskedKey())
        binding.clearKeyButton.visibility =
            if (AIConfig.isUsingRuntimeKey) View.VISIBLE else View.GONE
    }

    private fun saveKey() {
        val key = binding.apiKeyInput.text?.toString()?.trim().orEmpty()

        // Cheap client-side sanity checks so an obviously-wrong paste fails here rather
        // than as a confusing network error later.
        val error = when {
            key.isBlank() -> getString(R.string.ai_error_empty)
            key.length < 20 -> getString(R.string.ai_error_too_short)
            key.contains(" ") -> getString(R.string.ai_error_whitespace)
            else -> null
        }
        if (error != null) {
            binding.apiKeyLayout.error = error
            return
        }
        binding.apiKeyLayout.error = null

        AIConfig.saveRuntimeKey(key, selectedModel())
        binding.apiKeyInput.setText("")
        binding.testResultText.visibility = View.GONE
        renderStatus()
        Toast.makeText(this, R.string.ai_key_saved, Toast.LENGTH_SHORT).show()
    }

    /**
     * Sends one tiny real prompt so the admin gets a definitive yes/no instead of
     * discovering a bad key later inside the student/organizer AI flows.
     */
    private fun testConnection() {
        if (!AIConfig.isConfigured) {
            Toast.makeText(this, R.string.ai_error_save_first, Toast.LENGTH_SHORT).show()
            return
        }

        binding.testProgress.visibility = View.VISIBLE
        binding.testResultText.visibility = View.GONE
        binding.testConnectionButton.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    GeminiAIDataSource().answerWithFacts(
                        systemPrompt = "Reply with the single word OK.",
                        userQuery = "Connection test",
                        facts = ""
                    )
                }
            }

            binding.testProgress.visibility = View.GONE
            binding.testConnectionButton.isEnabled = true
            binding.testResultText.visibility = View.VISIBLE

            result.fold(
                onSuccess = { response ->
                    if (response.isNullOrBlank()) {
                        showTestResult(getString(R.string.ai_test_empty), isError = true)
                    } else {
                        showTestResult(
                            getString(R.string.ai_test_success, AIConfig.model),
                            isError = false
                        )
                    }
                },
                onFailure = { e ->
                    val message = e.message.orEmpty()
                    val friendly = when {
                        message.contains("400") || message.contains("403") ->
                            getString(R.string.ai_test_bad_key)
                        message.contains("404") -> getString(R.string.ai_test_bad_model)
                        message.contains("429") -> getString(R.string.ai_test_rate_limited)
                        else -> getString(R.string.ai_test_failed, message)
                    }
                    showTestResult(friendly, isError = true)
                }
            )
        }
    }

    private fun showTestResult(message: String, isError: Boolean) {
        binding.testResultText.text = message
        binding.testResultText.setTextColor(
            getColor(if (isError) R.color.error else R.color.success)
        )
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_clear_key)
            .setMessage(R.string.ai_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_clear_action) { _, _ ->
                AIConfig.clearRuntimeKey()
                binding.testResultText.visibility = View.GONE
                renderStatus()
                Toast.makeText(this, R.string.ai_key_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
