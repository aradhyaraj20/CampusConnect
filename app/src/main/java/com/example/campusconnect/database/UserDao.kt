package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserByIdFlow(id: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    @Query("UPDATE users SET role = :role WHERE id = :userId")
    suspend fun updateRole(userId: Int, role: String): Int

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = :role")
    suspend fun getUserCountByRole(role: String): Int

    @Query("UPDATE users SET account_status = :status WHERE id = :userId")
    suspend fun updateAccountStatus(userId: Int, status: String): Int

    @Query("SELECT * FROM users WHERE role = :role ORDER BY name ASC")
    fun getUsersByRole(role: String): kotlinx.coroutines.flow.Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY karma_points DESC LIMIT :limit")
    fun getTopUsers(limit: Int): Flow<List<User>>

    // Competition (karma leaderboard) is scoped to classmates/batchmates on the
    // same course, rather than the whole app - students only compete with peers
    // taking the same course.
    @Query("SELECT * FROM users WHERE course = :course ORDER BY karma_points DESC LIMIT :limit")
    fun getTopUsersByCourse(course: String, limit: Int): Flow<List<User>>

    // --- Scalability: paginated + searchable admin user listing -----------
    // Loading every user into memory (getAllUsers) does not scale once there
    // are thousands of accounts. Admin screens page through LIMIT/OFFSET
    // instead, backed by indices on email/role/course/account_status.
    @Query(
        """
        SELECT * FROM users
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%')
        AND (:role = 'ALL' OR role = :role)
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getUsersPage(query: String, role: String, limit: Int, offset: Int): List<User>

    @Query(
        """
        SELECT COUNT(*) FROM users
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%')
        AND (:role = 'ALL' OR role = :role)
        """
    )
    suspend fun getUsersPageCount(query: String, role: String): Int

    // --- Auto-cleanup: course ended AND inactive for the cutoff window -----
    @Query(
        """
        SELECT * FROM users
        WHERE role = 'STUDENT'
        AND course_end_at IS NOT NULL
        AND course_end_at < :now
        AND last_login_at < :inactivityCutoff
        """
    )
    suspend fun getStaleAccounts(now: Long, inactivityCutoff: Long): List<User>

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Int)

    @Query("UPDATE users SET last_login_at = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: Int, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

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
    suspend fun updateAvatar(userId: Int, avatarUri: String): Int

    @Query("UPDATE users SET password_hash = :passwordHash WHERE id = :userId")
    suspend fun updatePassword(userId: Int, passwordHash: String): Int

    // Karma
    @Query("UPDATE users SET karma_points = karma_points + :points WHERE id = :userId")
    suspend fun addKarma(userId: Int, points: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKarmaTransaction(transaction: KarmaTransaction)

    @Query("SELECT * FROM karma_transactions WHERE from_user_id = :fromId AND to_user_id = :toId AND event_id = :eventId")
    suspend fun getKarmaTransaction(fromId: Int, toId: Int, eventId: Int): KarmaTransaction?

    // Interests
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterest(interest: UserInterest)

    @Query("SELECT * FROM user_interests WHERE user_id = :userId")
    fun getInterests(userId: Int): Flow<List<UserInterest>>

    // Search History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(history: SearchHistory)

    @Query("DELETE FROM search_history WHERE user_id = :userId AND query = :query")
    suspend fun deleteSearchQuery(userId: Int, query: String): Int

    @Query("SELECT * FROM search_history WHERE user_id = :userId ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearch(userId: Int): Flow<List<SearchHistory>>
}
