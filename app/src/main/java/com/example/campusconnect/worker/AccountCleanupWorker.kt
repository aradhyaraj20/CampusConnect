package com.example.campusconnect.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.campusconnect.R
import com.example.campusconnect.database.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Runs once a day. Deletes STUDENT accounts whose course has already ended
 * AND who have not opened the app for INACTIVITY_WINDOW_MILLIS (2 months)
 * since. This keeps the users table from accumulating stale rows for courses
 * that finished long ago, which also keeps admin screens and leaderboard
 * queries fast as enrollment grows into the thousands.
 *
 * Organizers and admins are never auto-deleted; only STUDENT rows are
 * considered (see UserDao.getStaleAccounts).
 */
class AccountCleanupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val now = System.currentTimeMillis()
            val inactivityCutoff = now - INACTIVITY_WINDOW_MILLIS

            val stale = db.userDao().getStaleAccounts(now, inactivityCutoff)
            stale.forEach { user ->
                db.eraseUser(user.id) { title ->
                    applicationContext.getString(R.string.event_deleted_notice_title) to
                        applicationContext.getString(R.string.event_deleted_notice_message, title)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "account_cleanup_worker"
        val INACTIVITY_WINDOW_MILLIS: Long = TimeUnit.DAYS.toMillis(60) // ~2 months

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccountCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
