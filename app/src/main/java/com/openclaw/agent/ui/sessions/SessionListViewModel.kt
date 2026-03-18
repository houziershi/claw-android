package com.openclaw.agent.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.agent.core.session.SessionManager
import com.openclaw.agent.data.db.entities.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    val sessions: Flow<List<SessionEntity>> = sessionManager.allSessions

    fun createNewSession(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val session = sessionManager.createSession()
            onCreated(session.id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionManager.deleteSession(sessionId)
        }
    }
}
