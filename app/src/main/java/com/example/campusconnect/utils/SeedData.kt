package com.example.campusconnect.utils

import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.core.security.CryptoUtils
import androidx.room.withTransaction
import com.example.campusconnect.domain.event.EventTime
import com.example.campusconnect.models.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object SeedData {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Seed events are dated relative to install day so the feed is never stale. */
    private fun dayMillis(offsetDays: Int): Long = System.currentTimeMillis() + offsetDays * DAY_MS

    private fun dayLabel(offsetDays: Int): String =
        DateFormats.eventDay(Date(dayMillis(offsetDays)))

    /** Demo password for the debug accounts; meets the app's own 8-character minimum. */
    const val DEMO_PASSWORD = "campus123"

    /**
     * Release builds never get the demo accounts (known demo credentials) or
     * demo content. If ADMIN_INITIAL_PASSWORD is configured at build time, they get
     * a single admin account that must change its password at first login.
     */
    private suspend fun seedProduction(db: AppDatabase) {
        val userDao = db.userDao()
        val adminPassword = com.example.campusconnect.BuildConfig.ADMIN_INITIAL_PASSWORD
        if (adminPassword.length >= 8 && userDao.getUserCountByRole("ADMIN") == 0) {
            userDao.insertUser(
                com.example.campusconnect.models.User(
                    name = "Campus Admin",
                    email = "admin@campus.edu",
                    password_hash = CryptoUtils.hashPassword(adminPassword),
                    created_at = DateFormats.storedTimestamp(),
                    role = "ADMIN",
                    // The password is compiled into the APK, so it only works until first login.
                    must_change_password = true
                )
            )
        }
    }

    suspend fun seedIfEmpty(db: AppDatabase) {
        withContext(Dispatchers.IO) {
            if (!com.example.campusconnect.BuildConfig.DEBUG) {
                seedProduction(db)
                return@withContext
            }
            // Demo accounts all use DEMO_PASSWORD (see README), stored as a salted PBKDF2 hash.
            val demoHash = CryptoUtils.hashPassword(DEMO_PASSWORD)
            // One transaction: an interrupted first launch leaves nothing half-seeded.
            db.withTransaction { seedDebug(db, demoHash) }
        }
    }

    private suspend fun seedDebug(db: AppDatabase, demoHash: String) {
        // Seed Users. Each demo account is only added if it is missing, so deleting one
        // never re-inserts (and overwrites) the others.
        val userDao = db.userDao()
        run {
            val users = listOf(
                com.example.campusconnect.models.User(
                    id = 1,
                    name = "Aradhya Raj",
                    email = "aradhya@email.com",
                    password_hash = demoHash,
                    created_at = "2026-08-16",
                    karma_points = 150,
                    avatar_uri = "https://api.dicebear.com/7.x/adventurer/png?seed=Felix",
                    bio = "Passionate Android Developer and Coffee Lover.",
                    interests = "Tech, Android, Coffee",
                    role = "STUDENT"
                ),
                com.example.campusconnect.models.User(
                    id = 2,
                    name = "CS Society",
                    email = "cs@campus.edu",
                    password_hash = demoHash,
                    created_at = "2026-08-16",
                    karma_points = 500,
                    avatar_uri = "https://api.dicebear.com/7.x/avataaars/png?seed=Aneka",
                    bio = "Official Computer Science Society. Hosting the best workshops!",
                    interests = "Coding, Workshops, Community",
                    role = "ORGANIZER"
                ),
                com.example.campusconnect.models.User(
                    id = 3,
                    name = "Campus Admin",
                    email = "admin@campus.edu",
                    password_hash = demoHash,
                    created_at = "2026-08-16",
                    karma_points = 0,
                    bio = "Platform administrator.",
                    interests = null,
                    role = "ADMIN"
                )
            )
            users.filter { userDao.getUserById(it.id) == null && userDao.getUserByEmail(it.email) == null }
                .forEach { userDao.insertUser(it) }
        }

        val eventDao = db.eventDao()
        if (eventDao.getEventCount() == 0) {
            val events = listOf(
                Event(
                    title = "Hackathon Night",
                    description = "An overnight coding sprint open to all departments.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800",
                    date = dayLabel(2),
                    time = "18:00",
                    location = "Innovation Block C",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 50,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(2)
                ),
                Event(
                    title = "DBMS Study Group",
                    description = "Weekly session to discuss database normalization and SQL.",
                    type = "study_group",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=800",
                    date = "Every Tue",
                    time = "19:00",
                    location = "Central Library",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 10,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = System.currentTimeMillis() // Recurring
                ),
                Event(
                    title = "Basketball Finals",
                    description = "The ultimate showdown between Faculty of CS and Business.",
                    type = "event",
                    category = "Sports",
                    image_uri = "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=800",
                    date = dayLabel(10),
                    time = "16:00",
                    location = "Campus Arena",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 200,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(10)
                ),
                Event(
                    title = "Late Night Jazz",
                    description = "Relax with live jazz performances from student bands.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=800",
                    date = dayLabel(12),
                    time = "21:00",
                    location = "Student Union Cafe",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 100,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(12)
                ),
                Event(
                    title = "UI/UX Workshop",
                    description = "Learn the fundamentals of Figma and user-centric design.",
                    type = "study_group",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1586717791821-3f44a563eb4c?w=800",
                    date = dayLabel(15),
                    time = "14:00",
                    location = "Design Studio 2",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 25,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(15)
                ),
                Event(
                    title = "Outdoor Yoga",
                    description = "Morning yoga session at the campus gardens.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=800",
                    date = dayLabel(18),
                    time = "07:30",
                    location = "East Gardens",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 30,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(18)
                ),
                Event(
                    title = "Android Help for Coffee",
                    description = "I can help with Android Studio setup",
                    type = "event",
                    category = "Skill Swap",
                    image_uri = "https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=800",
                    date = dayLabel(6),
                    time = "10:00",
                    location = "Library Cafe",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 1,
                    is_user_created = false,
                    creator_id = 1,
                    date_timestamp = dayMillis(6)
                ),
                Event(
                    title = "Winter Gala 2025",
                    description = "A night of elegance and dance from last winter.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?w=800",
                    date = dayLabel(-238),
                    time = "20:00",
                    location = "Grand Ballroom",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 500,
                    is_user_created = false,
                    creator_id = 2,
                    date_timestamp = dayMillis(-238)
                ),
                Event(
                    title = "Need Math Tutor NOW",
                    description = "Solving calculus derivatives in library room 4",
                    type = "event",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=800",
                    date = "Today",
                    time = "Live",
                    location = "Library Room 4",
                    map_lat = null,
                    map_lng = null,
                    max_attendees = 2,
                    is_user_created = false,
                    creator_id = 2,
                    is_flash = true,
                    expiry_time = System.currentTimeMillis() + 1800000, // 30 mins from now
                    date_timestamp = System.currentTimeMillis()
                ),
                // September Events
                Event(
                    title = "Fresher's Welcome Party",
                    description = "Welcome all new students to the campus! Music, food, and fun.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800",
                    date = dayLabel(26),
                    time = "19:00",
                    location = "Main Quad",
                    map_lat = null, map_lng = null, max_attendees = 500,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(26)
                ),
                Event(
                    title = "Python for Beginners",
                    description = "Starting a new series for those new to programming.",
                    type = "study_group",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=800",
                    date = dayLabel(33),
                    time = "15:00",
                    location = "Lab 101",
                    map_lat = null, map_lng = null, max_attendees = 30,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(33)
                ),
                // October Events
                Event(
                    title = "Campus Tech Fest",
                    description = "Annual technology festival showcasing student projects.",
                    type = "event",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800",
                    date = dayLabel(66),
                    time = "10:00",
                    location = "Convention Center",
                    map_lat = null, map_lng = null, max_attendees = 1000,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(66)
                ),
                Event(
                    title = "Halloween Night",
                    description = "Spooky costumes and scary movies in the auditorium.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=800",
                    date = dayLabel(82),
                    time = "20:00",
                    location = "Auditorium",
                    map_lat = null, map_lng = null, max_attendees = 300,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(82)
                ),
                // November Events
                Event(
                    title = "Career Fair 2026",
                    description = "Meet recruiters from top companies across the country.",
                    type = "event",
                    category = "Professional",
                    image_uri = "https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=800",
                    date = dayLabel(92),
                    time = "09:00",
                    location = "Sports Complex",
                    map_lat = null, map_lng = null, max_attendees = 2000,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(92)
                ),
                Event(
                    title = "Algo Masters Group",
                    description = "Preparing for coding interviews with advanced algorithms.",
                    type = "study_group",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1516116216624-53e697fedbea?w=800",
                    date = dayLabel(100),
                    time = "17:00",
                    location = "CS Block Lounge",
                    map_lat = null, map_lng = null, max_attendees = 15,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(100)
                ),
                // December Events
                Event(
                    title = "Annual Science Exhibition",
                    description = "Innovation and research on display by students.",
                    type = "event",
                    category = "Academic",
                    image_uri = "https://images.unsplash.com/photo-1532094349884-543bc11b234d?w=800",
                    date = dayLabel(117),
                    time = "11:00",
                    location = "Exhibition Hall",
                    map_lat = null, map_lng = null, max_attendees = 400,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(117)
                ),
                Event(
                    title = "New Year's Eve Concert",
                    description = "End the year with a bang and live student bands.",
                    type = "event",
                    category = "Social",
                    image_uri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800",
                    date = dayLabel(143),
                    time = "22:00",
                    location = "Grand Lawn",
                    map_lat = null, map_lng = null, max_attendees = 1000,
                    is_user_created = false, creator_id = 2,
                    date_timestamp = dayMillis(143)
                )
            )
            events.map { e ->
                if (TIME_LABEL.matches(e.time)) e.copy(date_timestamp = EventTime.combine(e.date_timestamp, e.time)) else e
            }.forEach { event ->
                val eventId = eventDao.insertEvent(event).toInt()
                
                // Seed a poll for the first event
                if (event.title == "Hackathon Night") {
                    val pollId = db.pollDao().insertPoll(com.example.campusconnect.models.Poll(
                        event_id = eventId,
                        question = "Which workshop track should we run first?",
                        ends_at = "20:00"
                    )).toInt()
                    
                    db.pollDao().insertPollOptions(listOf(
                        com.example.campusconnect.models.PollOption(poll_id = pollId, option_text = "Web Development", vote_count = 45),
                        com.example.campusconnect.models.PollOption(poll_id = pollId, option_text = "Mobile Development", vote_count = 30),
                        com.example.campusconnect.models.PollOption(poll_id = pollId, option_text = "AI/ML", vote_count = 25)
                    ))
                }

                // Seed memories for past event
                if (event.title == "Winter Gala 2025") {
                    val photos = listOf(
                        "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=800",
                        "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800",
                        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800"
                    )
                    photos.forEach { uri ->
                        db.eventDao().addRecapPhoto(com.example.campusconnect.models.RecapPhoto(
                            event_id = eventId,
                            user_id = 1,
                            image_uri = uri,
                            posted_at = "2025-12-16"
                        ))
                    }
                }
            }
            
            // Seed Marketplace
            val marketplaceDao = db.marketplaceDao()
            if (marketplaceDao.getItemCount() == 0) {
                val items = listOf(
                    com.example.campusconnect.models.MarketplaceItem(
                        title = "Calculus Textbook",
                        description = "Slightly used, 5th edition",
                        category = "Textbooks",
                        price = 300.0,
                        image_uri = null,
                        poster_id = 1,
                        posted_at = "2026-08-15"
                    ),
                    com.example.campusconnect.models.MarketplaceItem(
                        title = "Ride to Airport",
                        description = "Leaving Sunday at 4 AM",
                        category = "Rides",
                        price = 0.0,
                        image_uri = null,
                        poster_id = 2,
                        posted_at = "2026-08-15"
                    ),
                    com.example.campusconnect.models.MarketplaceItem(
                        title = "Android Help for Coffee",
                        description = "I can help with Android Studio setup",
                        category = "Skill Swap",
                        price = 0.0,
                        image_uri = null,
                        poster_id = 1,
                        posted_at = "2026-08-16",
                        is_swap = true,
                        exchange_with = "1 cup of Latte"
                    ),
                    com.example.campusconnect.models.MarketplaceItem(
                        title = "Lost Blue Backpack",
                        description = "Contains library books and a calculator. Lost near C block.",
                        category = "Lost & Found",
                        price = 50.0, // This will be treated as Karma Bounty
                        image_uri = "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800",
                        poster_id = 1,
                        posted_at = "2026-08-16"
                    )
                )
                items.forEach { marketplaceDao.insertItem(it) }
            }

            // Seed Notifications
            val notificationDao = db.notificationDao()
            if (notificationDao.getNotificationCount(1) == 0) {
                 notificationDao.insertNotification(com.example.campusconnect.models.Notification(
                    user_id = 1,
                    title = "Welcome to CampusConnect!",
                    message = "Start exploring events and groups around you.",
                    timestamp = System.currentTimeMillis(),
                    type = "system"
                ))
                notificationDao.insertNotification(com.example.campusconnect.models.Notification(
                    user_id = 1,
                    title = "New Flash Session!",
                    message = "Need Math Tutor NOW is happening live near you.",
                    timestamp = System.currentTimeMillis() - 600000,
                    type = "flash"
                ))
            }

            // Seed Reports (first seed only, so resolved demo reports stay resolved)
            val reportDao = db.reportDao()
            reportDao.insertReport(
                com.example.campusconnect.models.Report(
                    reportedBy = 1,
                    targetType = "EVENT",
                    targetId = 1,
                    reason = "Event description looks misleading.",
                    status = "PENDING",
                    timestamp = System.currentTimeMillis() - 3600000
                )
            )
            reportDao.insertReport(
                com.example.campusconnect.models.Report(
                    reportedBy = 1,
                    targetType = "USER",
                    targetId = 2,
                    reason = "Spam messages in event chat.",
                    status = "PENDING",
                    timestamp = System.currentTimeMillis() - 1800000
                )
            )
        }
    }

    private val TIME_LABEL = Regex("^\\d{1,2}:\\d{2}$")
}
