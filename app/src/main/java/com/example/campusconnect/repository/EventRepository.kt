package com.example.campusconnect.repository

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.RSVP
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventRepository(private val eventDao: EventDao, private val rsvpDao: RsvpDao) {

    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()

    suspend fun getEventById(id: Int): Event? = eventDao.getEventById(id)

    suspend fun insertEvent(event: Event): Long = eventDao.insertEvent(event)

    suspend fun enrollUser(rsvp: RSVP) = rsvpDao.insertRSVP(rsvp)

    fun isUserEnrolled(userId: Int, eventId: Int): Flow<Boolean> {
        // We can check if getRsvp returns non-null
        return rsvpDao.getRsvpsForEvent(eventId).map { rsvps ->
            rsvps.any { it.user_id == userId }
        }
    }
}
