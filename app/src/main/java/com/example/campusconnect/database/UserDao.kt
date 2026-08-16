package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.User
import com.example.campusconnect.models.Friendship
import com.example.campusconnect.models.Setting
import com.example.campusconnect.models.KarmaTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE email = :email AND password_hash = :password")
    suspend fun login(email: String, password: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserByIdFlow(id: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    // Friendships
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriend(friendship: Friendship)

    @Query("SELECT users.* FROM users INNER JOIN friendships ON users.id = friendships.friend_id WHERE friendships.user_id = :userId")
    fun getFriends(userId: Int): Flow<List<User>>

    // Settings
    @Query("SELECT * FROM settings WHERE user_id = :userId")
    suspend fun getSettings(userId: Int): Setting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(setting: Setting)

    @Query("UPDATE users SET avatar_uri = :avatarUri WHERE id = :userId")
    suspend fun updateAvatar(userId: Int, avatarUri: String)

    // Karma
    @Query("UPDATE users SET karma_points = karma_points + :points WHERE id = :userId")
    suspend fun addKarma(userId: Int, points: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKarmaTransaction(transaction: KarmaTransaction)

    @Query("SELECT * FROM karma_transactions WHERE from_user_id = :fromId AND to_user_id = :toId AND event_id = :eventId")
    suspend fun getKarmaTransaction(fromId: Int, toId: Int, eventId: Int): KarmaTransaction?
}
