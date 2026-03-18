package com.openclaw.agent.core.session

import com.openclaw.agent.data.db.MessageDao
import com.openclaw.agent.data.db.SessionDao
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer for session data access.
 * Wraps DAOs and provides a clean API for the domain layer.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun getSession(id: String): SessionEntity? = sessionDao.getSession(id)

    suspend fun insertSession(session: SessionEntity) = sessionDao.insertSession(session)

    suspend fun deleteSession(id: String) {
        messageDao.deleteMessagesForSession(id)
        sessionDao.deleteSessionById(id)
    }

    fun getMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun getMessagesOnce(sessionId: String): List<MessageEntity> =
        messageDao.getMessagesForSessionOnce(sessionId)

    suspend fun insertMessage(message: MessageEntity) = messageDao.insertMessage(message)

    suspend fun getRecentMessages(sessionId: String, limit: Int): List<MessageEntity> =
        messageDao.getRecentMessages(sessionId, limit)
}
