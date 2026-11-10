package com.example.nightwatchprotocol.cybermind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorCsvBytesTest {

    private val demo: ByteArray by lazy { checkNotNull(javaClass.getResourceAsStream("/estudiantes_demo.csv")).use { it.readBytes() } }

    @Test
    fun csvDemo_dalosConteosParaLaTarjeta() {
        val l = ValidadorCsv.validarBytes(demo)
        assertTrue(l.resultado is ResultadoValidacionCsv.Valido)
        assertEquals(500, l.filasDeDatos)
        assertEquals(100, l.estudiantes)
    }

    @Test
    fun utf8Invalido_seRechaza() {
        val malo = "id_estudiante,tema\n".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28)
        val l = ValidadorCsv.validarBytes(malo)
        assertEquals(listOf<ErrorCsv>(ErrorCsv.NoEsUtf8), (l.resultado as ResultadoValidacionCsv.Invalido).errores)
    }

    @Test
    fun masDe200KB_seRechazaSinDecodificar() {
        val l = ValidadorCsv.validarBytes(ByteArray(200 * 1024 + 1) { 'a'.code.toByte() })
        assertEquals(
            listOf<ErrorCsv>(ErrorCsv.ArchivoDemasiadoGrande(200L * 1024L + 1)),
            (l.resultado as ResultadoValidacionCsv.Invalido).errores,
        )
    }

    @Test
    fun archivoInvalido_tambienCuentaSusFilas() {
        val texto = ValidadorCsv.COLUMNAS.joinToString(",") + ",decision\nE1,x\nE2,y\nE2,z\n"
        val l = ValidadorCsv.validarBytes(texto.toByteArray())
        assertTrue(l.resultado is ResultadoValidacionCsv.Invalido)
        assertEquals(3, l.filasDeDatos)
        assertEquals(2, l.estudiantes)
    }
}
