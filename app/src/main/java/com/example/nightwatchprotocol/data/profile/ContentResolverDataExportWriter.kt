package com.example.nightwatchprotocol.data.profile

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentResolverDataExportWriter(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataExportWriter {

    override suspend fun write(destination: String, content: String) {
        withContext(ioDispatcher) {
            // "wt" truncates, so exporting again over the same file never leaves old bytes at the end.
            val stream = contentResolver.openOutputStream(Uri.parse(destination), "wt")
                ?: throw IOException("No se pudo abrir el archivo")
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
    }
}
