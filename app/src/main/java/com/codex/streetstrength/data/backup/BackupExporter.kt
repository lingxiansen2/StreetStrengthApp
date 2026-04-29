package com.codex.streetstrength.data.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupExporter {
    suspend fun readJson(
        context: Context,
        uri: Uri,
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup source.")
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    suspend fun writeJson(
        context: Context,
        uri: Uri,
        json: String,
    ) = withContext(Dispatchers.IO) {
        val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open backup destination.")
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.flush()
        }
    }
}
