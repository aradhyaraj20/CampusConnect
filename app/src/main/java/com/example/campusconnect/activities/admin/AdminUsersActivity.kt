package com.example.campusconnect.activities.admin

import com.example.campusconnect.core.common.showMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.AdminUserAdapter
import com.example.campusconnect.appContainer
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAdminUsersBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.domain.usecase.ModerateReportUseCase
import com.example.campusconnect.worker.AccountCleanupWorker
import kotlinx.coroutines.launch

/**
 * Scalability: instead of loading every row in `users` into memory (which
 * degrades once enrollment reaches the thousands), this screen pages through
 * the table with LIMIT/OFFSET (UserDao.getUsersPage) and lets the admin
 * narrow by name/email search or role before paging - see AppDatabase's
 * indices on email/role/course/account_status backing these queries.
 */
class AdminUsersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminUsersBinding
    private lateinit var adapter: AdminUserAdapter
    private lateinit var useCase: ModerateReportUseCase

    private var searchQuery: String = ""
    private var roleFilter: String = "ALL"
    private var pageIndex: Int = 0
    private val pageSize = 5
    private var totalCount: Int = 0
    private lateinit var pagerBar: com.example.campusconnect.core.common.PagerBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.user_management)

        if (!AccessPolicy.canAccessAdmin(SessionManager.getInstance(this).getCurrentUserRole())) {
            Toast.makeText(this, R.string.admin_access_required, Toast.LENGTH_SHORT).show(); finish(); return
        }

        val db = AppDatabase.getDatabase(this)
        useCase = appContainer.moderationUseCase(SessionManager.getInstance(this).getCurrentUserId())

        adapter = AdminUserAdapter(emptyList(),
            onWarn = { user ->
                lifecycleScope.launch {
                    useCase.warnUser(user.id).showResult(this@AdminUsersActivity, getString(R.string.user_warned_format, user.name))
                    resetAndLoad()
                }
            },
            onSuspend = { user ->
                lifecycleScope.launch {
                    useCase.suspendUser(user.id).showResult(this@AdminUsersActivity, getString(R.string.user_suspended_format, user.name))
                    resetAndLoad()
                }
            },
            // Long-press a row: promote/demote between Student and Organizer (admins are never changed here).
            onChangeRole = { user ->
                val newRole = if (user.role == "ORGANIZER") "STUDENT" else "ORGANIZER"
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.change_role)
                    .setMessage(getString(R.string.change_role_message_format, user.name, newRole.lowercase()))
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        lifecycleScope.launch {
                            useCase.changeRole(user.id, newRole)
                                .showResult(this@AdminUsersActivity, getString(R.string.user_role_changed_format, user.name, newRole))
                            resetAndLoad()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.list_columns))
        binding.recyclerView.adapter = adapter

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                resetAndLoad()
            }
        })

        binding.roleFilterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            roleFilter = when (checkedIds.firstOrNull()) {
                binding.chipStudent.id -> "STUDENT"
                binding.chipOrganizer.id -> "ORGANIZER"
                binding.chipAdmin.id -> "ADMIN"
                else -> "ALL"
            }
            resetAndLoad()
        }

        binding.loadMoreButton.visibility = View.GONE
        pagerBar = com.example.campusconnect.core.common.PagerBar.attachBelow(binding.recyclerView)
        pagerBar.onPrev = { if (pageIndex > 0) { pageIndex--; loadPage() } }
        pagerBar.onNext = { pageIndex++; loadPage() }

        resetAndLoad()
    }

    private fun resetAndLoad() {
        pageIndex = 0
        loadPage()
    }

    /** Server-side paging: only the 5 rows of the current page are ever read from the database. */
    private fun loadPage() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@AdminUsersActivity)
            totalCount = db.userDao().getUsersPageCount(searchQuery, roleFilter)
            val pages = ((totalCount + pageSize - 1) / pageSize).coerceAtLeast(1)
            pageIndex = pageIndex.coerceIn(0, pages - 1)
            val page = db.userDao().getUsersPage(searchQuery, roleFilter, pageSize, pageIndex * pageSize)
            adapter.submit(page)

            binding.emptyState.visibility = if (totalCount == 0) View.VISIBLE else View.GONE
            binding.resultCountText.text = getString(R.string.showing_of_users_format, page.size, totalCount)
            pagerBar.show(pageIndex, pages)
            binding.recyclerView.scrollToPosition(0)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.admin_users_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_manage_ads -> {
                startActivity(android.content.Intent(this, AdminAdsActivity::class.java))
                true
            }
            R.id.action_run_cleanup -> {
                runCleanupNow()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runCleanupNow() {
        val request = androidx.work.OneTimeWorkRequestBuilder<AccountCleanupWorker>().build()
        androidx.work.WorkManager.getInstance(this).enqueue(request)
        showMessage(R.string.inactive_account_cleanup_started)
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
