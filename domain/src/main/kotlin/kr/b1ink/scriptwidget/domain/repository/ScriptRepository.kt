package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ScriptRepository {
    /** Sorted script names, updated on every change. */
    val scriptNames: StateFlow<List<String>>

    suspend fun list(): List<String>
    suspend fun read(name: String): String?
    suspend fun exists(name: String): Boolean
    suspend fun write(name: String, content: String)

    /** @return false if [from] doesn't exist or [to] already exists. */
    suspend fun rename(from: String, to: String): Boolean
    suspend fun delete(name: String)
}
