package com.example.campusconnect

import android.content.Context
import com.example.campusconnect.core.common.RoomTransactionRunner
import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.domain.usecase.ModerateReportUseCase
import com.example.campusconnect.domain.usecase.PollUseCases

/**
 * Hand-rolled dependency container (no DI framework): screens ask it for the database, the
 * transaction runner and the use cases instead of wiring DAOs themselves. Reach it with
 * `context.appContainer`.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getDatabase(appContext) }
    val transactions: TransactionRunner by lazy { RoomTransactionRunner(database) }

    /** [actorId] is the signed-in admin; the use case re-checks their role on every action. */
    fun moderationUseCase(actorId: Int) =
        ModerateReportUseCase(database.reportDao(), database.userDao(), database.eventDao(), actorId, transactions)

    fun pollUseCases() = PollUseCases(
        database.pollDao(), database.eventDao(), database.userDao(), database.rsvpDao(), database.notificationDao(), transactions
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CampusConnectApp).container
