package com.example.campusconnect.core.common

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block of DAO calls atomically. Use cases take this instead of a database so they stay
 * unit-testable on the JVM ([None]) while production code passes [RoomTransactionRunner].
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T

    object None : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}

class RoomTransactionRunner(private val db: RoomDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
