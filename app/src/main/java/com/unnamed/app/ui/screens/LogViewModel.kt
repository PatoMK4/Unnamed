package com.unnamed.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unnamed.app.UnnamedApp
import com.unnamed.app.logging.ParsedSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble in the logging chat. */
sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Draft(val parsed: ParsedSet) : ChatItem      // editable confirmation card
    data class Logged(val summary: String) : ChatItem
    data class Info(val text: String) : ChatItem
}

data class LogUiState(
    val items: List<ChatItem> = listOf(
        ChatItem.Info("Tell me what you did — e.g. \"incline db press 3x8 at 80kg rpe 8, shoulder felt heavy\". I'll show a card to confirm before saving."),
    ),
)

/**
 * Drives the confirm-before-commit chat loop. Today it parses locally
 * (SetParser); later the parse step calls the Claude-backed edge function,
 * but this state machine and the confirmation UX stay the same.
 */
class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as UnnamedApp
    private val _state = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    private var sessionId: String? = null

    /** User sent a message → parse it and append an editable draft card. */
    fun submit(message: String) {
        if (message.isBlank()) return
        val parsed = appCtx.parser.parse(message)
        _state.update {
            it.copy(items = it.items + ChatItem.User(message) + ChatItem.Draft(parsed))
        }
    }

    /** User confirmed (possibly after editing) the draft → persist it. */
    fun confirm(draft: ParsedSet) {
        viewModelScope.launch {
            val sid = sessionId ?: appCtx.repository.startSession().id.also { sessionId = it }
            appCtx.repository.commit(sid, draft)
            val name = draft.exercise?.name ?: draft.exercisePhrase
            val summary = buildString {
                append("Logged: $name")
                draft.reps?.let { append(" ${draft.sets}×$it") }
                draft.weightKg?.let { append(" @ ${it}kg") }
                draft.rpe?.let { append(" RPE $it") }
            }
            _state.update { st ->
                // replace the open draft card with a logged confirmation
                val items = st.items.toMutableList()
                val idx = items.indexOfLast { it is ChatItem.Draft }
                if (idx >= 0) items[idx] = ChatItem.Logged(summary)
                st.copy(items = items)
            }
        }
    }

    /** User dismissed the draft without saving. */
    fun discard(draft: ParsedSet) {
        _state.update { st ->
            st.copy(items = st.items.filterNot { it is ChatItem.Draft && it.parsed === draft })
        }
    }
}
