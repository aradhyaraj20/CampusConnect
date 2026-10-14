package com.example.campusconnect

import android.content.Context
import com.example.campusconnect.core.common.RoomTransactionRunner
import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.domain.usecase.ModerateReportUseCase

/**
 * Hand-rolled dependency container (no DI framework): screens ask it for the database, the
 * transaction runner and the use cases instead of wiring DAOs themselves. Reach it with
 * `context.appContainer`.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getDatabase(appContext) }
    val transactions: TransactionRunner by lazy { RoomTransactionRunner(database) }

    fun moderationUseCase() =
        ModerateReportUseCase(database.reportDao(), database.userDao(), database.eventDao(), transactions)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CampusConnectApp).container
