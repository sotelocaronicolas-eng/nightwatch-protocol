package com.example.nightwatchprotocol.cybermind

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Nombre y tamaño que informa el sistema para un archivo elegido ([bytes] es `null` si no lo informa). */
data class InfoArchivo(val nombre: String?, val bytes: Long?)

/**
 * Lee el CSV elegido y escribe la plantilla con el selector de archivos del sistema (sin permisos de almacenamiento). Los archivos se
 * identifican por el texto de su `Uri`, para que el ViewModel no dependa de Android. Bloqueante: se llama fuera del hilo principal.
 */
interface ArchivosCsv {
    fun info(uri: String): InfoArchivo

    /** Lee como mucho [maxBytes] bytes: si el archivo es más grande, el resultado trae [maxBytes] y ya basta para rechazarlo. */
    fun leer(uri: String, maxBytes: Long): ByteArray

    fun escribir(uri: String, texto: String)
}

class ContentResolverArchivosCsv(private val resolver: ContentResolver) : ArchivosCsv {

    override fun info(uri: String): InfoArchivo {
        val cursor = resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        return cursor?.use {
            if (!it.moveToFirst()) return@use InfoArchivo(null, null)
            val nombre = it.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { i -> i >= 0 }?.let { i -> it.getString(i) }
            val bytes = it.getColumnIndex(OpenableColumns.SIZE).takeIf { i -> i >= 0 && !it.isNull(i) }?.let { i -> it.getLong(i) }
            InfoArchivo(nombre, bytes)
        } ?: InfoArchivo(null, null)
    }

    override fun leer(uri: String, maxBytes: Long): ByteArray {
        val entrada = resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("No se pudo abrir el archivo")
        entrada.use { input ->
            val salida = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (salida.size() < maxBytes) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes - salida.size()).toInt())
                if (n < 0) break
                salida.write(buffer, 0, n)
            }
            return salida.toByteArray()
        }
    }

    override fun escribir(uri: String, texto: String) {
        val salida = resolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("No se pudo abrir el destino")
        salida.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
    }
}
