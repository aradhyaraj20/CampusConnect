package com.example.campusconnect.core.common

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.data.repository.AuthRepository
import com.example.campusconnect.data.repository.PasswordChangeResult
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.DialogChangePasswordBinding
import kotlinx.coroutines.launch

/**
 * The change-password dialog, shared by Settings and the forced change after a first login with
 * a build-provided password. Passwords are never trimmed: what is typed is what is stored.
 */
object ChangePasswordDialog {

    /**
     * @param knownCurrent when set (forced change right after login), the current-password field is
     *   hidden and this value is used, and the dialog cannot be cancelled.
     */
    fun show(
        activity: AppCompatActivity,
        knownCurrent: String? = null,
        userId: Int = SessionManager.getInstance(activity).getCurrentUserId(),
        onChanged: () -> Unit = {}
    ) {
        val b = DialogChangePasswordBinding.inflate(activity.layoutInflater)
        val forced = knownCurrent != null
        if (forced) b.currentPasswordLayout.visibility = View.GONE

        b.newPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = showStrength(activity, b, s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) {}
        })

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(if (forced) R.string.pwd_forced_title else R.string.settings_change_password)
            .setView(b.root)
            .setPositiveButton(R.string.pwd_update, null)
            .setCancelable(!forced)
        if (forced) builder.setMessage(R.string.pwd_forced_message) else builder.setNegativeButton(android.R.string.cancel, null)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(!forced)
        dialog.show()
        // Set after show() so validation failures don't dismiss the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            attempt(activity, b, dialog, userId, knownCurrent, onChanged)
        }
    }

    fun score(pwd: String): Int {
        var score = 0
        if (pwd.length >= 8) score++
        if (pwd.length >= 12) score++
        if (pwd.any { it.isDigit() }) score++
        if (pwd.any { it.isLetter() && it.isUpperCase() } && pwd.any { it.isLowerCase() }) score++
        if (pwd.any { !it.isLetterOrDigit() }) score++
        return score
    }

    private fun showStrength(activity: AppCompatActivity, b: DialogChangePasswordBinding, pwd: String) {
        if (pwd.isEmpty()) {
            b.strengthText.text = ""
            return
        }
        val s = score(pwd)
        val (label, color) = when {
            s <= 2 -> R.string.pwd_weak to R.color.error
            s == 3 -> R.string.pwd_fair to R.color.warning
            s == 4 -> R.string.pwd_good to R.color.success
            else -> R.string.pwd_strong to R.color.success
        }
        b.strengthText.setText(label)
        b.strengthText.setTextColor(activity.getColor(color))
    }

    private fun attempt(
        activity: AppCompatActivity,
        b: DialogChangePasswordBinding,
        dialog: AlertDialog,
        userId: Int,
        knownCurrent: String?,
        onChanged: () -> Unit
    ) {
        val current = knownCurrent ?: b.currentPasswordInput.text?.toString().orEmpty()
        val new = b.newPasswordInput.text?.toString().orEmpty()
        val confirm = b.confirmPasswordInput.text?.toString().orEmpty()

        b.currentPasswordLayout.error = null
        b.newPasswordLayout.error = null
        b.confirmPasswordLayout.error = null

        when {
            current.isEmpty() -> {
                b.currentPasswordLayout.error = activity.getString(R.string.pwd_err_current_required); return
            }
            new.length < AuthRepository.MIN_PASSWORD_LENGTH -> {
                b.newPasswordLayout.error = activity.getString(R.string.pwd_err_too_short); return
            }
            score(new) < 3 -> {
                b.newPasswordLayout.error = activity.getString(R.string.pwd_err_weak); return
            }
            new == current -> {
                b.newPasswordLayout.error = activity.getString(R.string.pwd_err_same); return
            }
            new != confirm -> {
                b.confirmPasswordLayout.error = activity.getString(R.string.pwd_err_mismatch); return
            }
        }

        activity.lifecycleScope.launch {
            val repo = AuthRepository(AppDatabase.getDatabase(activity).userDao(), SessionManager.getInstance(activity))
            when (repo.changePassword(userId, current, new)) {
                PasswordChangeResult.SUCCESS -> {
                    dialog.dismiss()
                    Toast.makeText(activity, R.string.pwd_updated, Toast.LENGTH_LONG).show()
                    onChanged()
                }
                PasswordChangeResult.NO_USER ->
                    Toast.makeText(activity, R.string.pwd_err_no_user, Toast.LENGTH_SHORT).show()
                PasswordChangeResult.LOCKED ->
                    b.currentPasswordLayout.error = activity.getString(R.string.pwd_err_locked)
                PasswordChangeResult.WRONG_CURRENT ->
                    b.currentPasswordLayout.error = activity.getString(R.string.pwd_err_current_wrong)
                PasswordChangeResult.TOO_SHORT ->
                    b.newPasswordLayout.error = activity.getString(R.string.pwd_err_too_short)
                PasswordChangeResult.SAME ->
                    b.newPasswordLayout.error = activity.getString(R.string.pwd_err_same)
            }
        }
    }
}
