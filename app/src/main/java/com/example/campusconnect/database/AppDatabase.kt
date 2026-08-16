package com.example.campusconnect.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.campusconnect.models.*

@Database(
    entities = [
        User::class,
        Event::class,
        RSVP::class,
        StudyGroupMember::class,
        Friendship::class,
        Message::class,
        Poll::class,
        PollOption::class,
        PollVote::class,
        RecapPhoto::class,
        Badge::class,
        UserBadge::class,
        MarketplaceItem::class,
        Setting::class,
        KarmaTransaction::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun rsvpDao(): RsvpDao
    abstract fun messageDao(): MessageDao
    abstract fun pollDao(): PollDao
    abstract fun badgeDao(): BadgeDao
    abstract fun marketplaceDao(): MarketplaceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_connect_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
