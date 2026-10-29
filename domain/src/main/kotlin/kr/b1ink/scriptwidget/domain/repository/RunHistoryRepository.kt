package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.b1ink.scriptwidget.domain.model.RunRecord

/** Recent script runs, newest first. */
interface RunHistoryRepository {
    val history: Flow<List<RunRecord>>

    suspend fun add(record: RunRecord)

    suspend fun clear()
}
