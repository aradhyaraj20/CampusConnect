package com.example.campusconnect.utils

import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.models.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SeedData {
    suspend fun seedIfEmpty(db: AppDatabase) {
        withContext(Dispatchers.IO) {
                // Seed Users
                val userDao = db.userDao()
                if (userDao.getUserById(1) == null) {
                    val users = listOf(
                        com.example.campusconnect.models.User(
                            id = 1,
                            name = "Aradhya Raj",
                            email = "aradhya@email.com",
                            password_hash = "password",
                            created_at = "2026-08-16",
                            karma_points = 150,
                            avatar_uri = "https://api.dicebear.com/7.x/adventurer/png?seed=Felix"
                        ),
                        com.example.campusconnect.models.User(
                            id = 2,
                            name = "CS Society",
                            email = "cs@campus.edu",
                            password_hash = "password",
                            created_at = "2026-08-16",
                            karma_points = 500,
                            avatar_uri = "https://api.dicebear.com/7.x/avataaars/png?seed=Aneka"
                        )
                    )
                    users.forEach { userDao.insertUser(it) }
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
                        date = "Aug 12, 2026",
                        time = "18:00",
                        location = "Innovation Block C",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 50,
                        is_user_created = false,
                        creator_id = 2
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
                        creator_id = 2
                    ),
                    Event(
                        title = "Basketball Finals",
                        description = "The ultimate showdown between Faculty of CS and Business.",
                        type = "event",
                        category = "Sports",
                        image_uri = "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=800",
                        date = "Aug 20, 2026",
                        time = "16:00",
                        location = "Campus Arena",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 200,
                        is_user_created = false,
                        creator_id = 2
                    ),
                    Event(
                        title = "Late Night Jazz",
                        description = "Relax with live jazz performances from student bands.",
                        type = "event",
                        category = "Social",
                        image_uri = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=800",
                        date = "Aug 22, 2026",
                        time = "21:00",
                        location = "Student Union Cafe",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 100,
                        is_user_created = false,
                        creator_id = 2
                    ),
                    Event(
                        title = "UI/UX Workshop",
                        description = "Learn the fundamentals of Figma and user-centric design.",
                        type = "study_group",
                        category = "Academic",
                        image_uri = "https://images.unsplash.com/photo-1586717791821-3f44a563eb4c?w=800",
                        date = "Aug 25, 2026",
                        time = "14:00",
                        location = "Design Studio 2",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 25,
                        is_user_created = false,
                        creator_id = 2
                    ),
                    Event(
                        title = "Outdoor Yoga",
                        description = "Morning yoga session at the campus gardens.",
                        type = "event",
                        category = "Social",
                        image_uri = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=800",
                        date = "Aug 28, 2026",
                        time = "07:30",
                        location = "East Gardens",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 30,
                        is_user_created = false,
                        creator_id = 2
                    ),
                    Event(
                        title = "Android Help for Coffee",
                        description = "I can help with Android Studio setup",
                        type = "event",
                        category = "Skill Swap",
                        image_uri = "https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=800",
                        date = "Aug 16, 2026",
                        time = "10:00",
                        location = "Library Cafe",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 1,
                        is_user_created = false,
                        creator_id = 1
                    ),
                    Event(
                        title = "Winter Gala 2025",
                        description = "A night of elegance and dance from last winter.",
                        type = "event",
                        category = "Social",
                        image_uri = "https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?w=800",
                        date = "Dec 15, 2025",
                        time = "20:00",
                        location = "Grand Ballroom",
                        map_lat = null,
                        map_lng = null,
                        max_attendees = 500,
                        is_user_created = false,
                        creator_id = 2
                    )
                )
                events.forEach { event ->
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
                
                // Seed Badges
                val badgeDao = db.badgeDao()
                if (badgeDao.getBadgeCount() == 0) {
                    val badges = listOf(
                        com.example.campusconnect.models.Badge(name = "First RSVP", description = "Unlocked by joining your first event", icon = "star"),
                        com.example.campusconnect.models.Badge(name = "5-Week Streak", description = "Attended sessions for 5 consecutive weeks", icon = "fire"),
                        com.example.campusconnect.models.Badge(name = "Study Buddy", description = "Joined 3 or more study groups", icon = "book")
                    )
                    badges.forEach { badgeDao.insertBadge(it) }
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
            }
        }
    }
}
