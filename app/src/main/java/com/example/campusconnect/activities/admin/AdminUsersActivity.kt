package com.example.campusconnect.activities.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.AdminUserAdapter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAdminUsersBinding
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
    private var pageOffset: Int = 0
    private val pageSize = 25
    private var totalCount: Int = 0
    private val loadedUsers = mutableListOf<com.example.campusconnect.models.User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.user_management)

        if (SessionManager.getInstance(this).getCurrentUserRole() != "ADMIN") {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show(); finish(); return
        }

        val db = AppDatabase.getDatabase(this)
        useCase = ModerateReportUseCase(db.reportDao(), db.userDao(), db.eventDao())

        adapter = AdminUserAdapter(emptyList(),
            onWarn = { user -> lifecycleScope.launch { useCase.warnUser(user.id); Toast.makeText(this@AdminUsersActivity, "${user.name} warned", Toast.LENGTH_SHORT).show() } },
            onSuspend = { user -> lifecycleScope.launch { useCase.suspendUser(user.id); Toast.makeText(this@AdminUsersActivity, "${user.name} suspended", Toast.LENGTH_SHORT).show() } },
            // Long-press a row: promote/demote between Student and Organizer (admins are never changed here).
            onChangeRole = { user ->
                val newRole = if (user.role == "ORGANIZER") "STUDENT" else "ORGANIZER"
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Change role")
                    .setMessage("Make ${user.name} a ${newRole.lowercase()}?")
                    .setPositiveButton("Confirm") { _, _ ->
                        lifecycleScope.launch {
                            db.userDao().updateRole(user.id, newRole)
                            Toast.makeText(this@AdminUsersActivity, "${user.name} is now $newRole", Toast.LENGTH_SHORT).show()
                            resetAndLoad()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
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

        binding.loadMoreButton.setOnClickListener { loadPage() }

        resetAndLoad()
    }

    private fun resetAndLoad() {
        pageOffset = 0
        loadedUsers.clear()
        adapter.submit(emptyList())
        loadPage()
    }

    private fun loadPage() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@AdminUsersActivity)
            totalCount = db.userDao().getUsersPageCount(searchQuery, roleFilter)
            val page = db.userDao().getUsersPage(searchQuery, roleFilter, pageSize, pageOffset)
            loadedUsers.addAll(page)
            pageOffset += page.size
            adapter.submit(loadedUsers.toList())

            binding.emptyState.visibility = if (loadedUsers.isEmpty()) View.VISIBLE else View.GONE
            binding.resultCountText.text = getString(R.string.showing_of_users_format, loadedUsers.size, totalCount)
            binding.loadMoreButton.visibility =
                if (pageOffset < totalCount) View.VISIBLE else View.GONE
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
        Toast.makeText(this, "Inactive-account cleanup started", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
