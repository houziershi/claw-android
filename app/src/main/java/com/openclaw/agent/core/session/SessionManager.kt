package com.openclaw.agent.core.session

import com.openclaw.agent.data.db.MessageDao
import com.openclaw.agent.data.db.SessionDao
import com.openclaw.agent.data.db.entities.MessageEntity
import com.openclaw.agent.data.db.entities.SessionEntity
import com.openclaw.agent.data.preferences.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val settingsStore: SettingsStore
) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun createSession(title: String = "New Chat"): SessionEntity {
        val model = settingsStore.selectedModelFlow.first()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            model = model
        )
        sessionDao.insertSession(session)
        return session
    }

    suspend fun getSession(id: String): SessionEntity? = sessionDao.getSession(id)

    fun getMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForSession(sessionId)

    suspend fun updateTitle(sessionId: String, title: String) {
        sessionDao.updateSessionTitle(sessionId, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) {
        messageDao.deleteMessagesForSession(sessionId)
        sessionDao.deleteSessionById(sessionId)
    }
}
