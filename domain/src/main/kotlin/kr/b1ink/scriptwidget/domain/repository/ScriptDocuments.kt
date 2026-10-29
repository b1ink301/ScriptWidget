package kr.b1ink.scriptwidget.domain.repository

import kr.b1ink.scriptwidget.domain.model.ScriptFile

/** Files the user picked outside the app (system file picker), addressed by URI. */
interface ScriptDocuments {
    /** @throws Exception when the document can't be opened or read. */
    suspend fun read(uri: String): ScriptFile

    suspend fun write(uri: String, content: String)

    /** @throws Exception when the document can't be opened or is too large. */
    suspend fun readBytes(uri: String): ByteArray

    suspend fun writeBytes(uri: String, content: ByteArray)
}

/** Packs scripts (file name → text) into one backup file and back. */
interface ScriptArchiveCodec {
    fun encode(files: Map<String, String>): ByteArray

    /** @throws java.io.IOException when [bytes] isn't a valid backup. */
    fun decode(bytes: ByteArray): Map<String, String>
}
