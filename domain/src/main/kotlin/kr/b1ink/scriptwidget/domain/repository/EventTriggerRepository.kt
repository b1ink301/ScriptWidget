package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.b1ink.scriptwidget.domain.model.EventTrigger

interface EventTriggerRepository {
    val triggers: Flow<List<EventTrigger>>

    suspend fun all(): List<EventTrigger>

    /** Adds the trigger, or replaces the one with the same id. */
    suspend fun save(trigger: EventTrigger)

    suspend fun remove(id: String)
}
