package com.example.nightwatchprotocol.ui.cybermind

import com.example.nightwatchprotocol.cybermind.ErrorCsv
import com.example.nightwatchprotocol.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorCsvTextoTest {

    @Test
    fun losMensajesDelFigma_salenTalCual() {
        assertEquals("Columna no permitida: decision", ErrorCsv.ColumnaNoPermitida("decision").aTexto().text())
        assertEquals(
            "Estudiante E00012: experiencia_previa distinta entre sus filas",
            ErrorCsv.ExperienciaDistinta("E00012").aTexto().text(),
        )
        assertEquals("Fila 14: aciertos_basico fuera de rango (0 a 3)", ErrorCsv.FueraDeRango(14, "aciertos_basico", "4").aTexto().text())
        assertEquals("Fila 31: tema no reconocido", ErrorCsv.ValorNoPermitido(31, "tema", "x").aTexto().text())
        assertEquals("Fila 90: puntaje_tema no coincide con los aciertos", ErrorCsv.PuntajeTemaIncoherente(90, 66.7, 70.0).aTexto().text())
    }

    @Test
    fun losRangos_dependenDeLaColumna() {
        assertEquals("Fila 2: tiempo_promedio_seg fuera de rango (4 a 180)", ErrorCsv.FueraDeRango(2, "tiempo_promedio_seg", "1").aTexto().text())
        assertEquals("Fila 2: puntaje_global fuera de rango (0 a 100)", ErrorCsv.FueraDeRango(2, "puntaje_global", "101").aTexto().text())
    }

    @Test
    fun elTitulo_diceCuantosSeMuestranDeCuantos() {
        assertEquals("El archivo no es válido (primeros 5 de 17 errores)", tituloErrores(17).text())
        assertEquals("El archivo no es válido (3 errores)", tituloErrores(3).text())
        assertEquals("El archivo no es válido (1 error)", tituloErrores(1).text())
        assertEquals("El archivo no es válido (5 errores)", tituloErrores(5).text())
    }

    @Test
    fun elResto_diceYNErroresMas() {
        assertEquals("… y 12 errores más", restoDeErrores(17).text())
        assertEquals("… y 1 error más", restoDeErrores(6).text())
        assertNull(restoDeErrores(5))
        assertNull(restoDeErrores(0))
    }

    @Test
    fun todosLosErrores_tienenTexto() {
        val todos = listOf(
            ErrorCsv.ArchivoDemasiadoGrande(300_000),
            ErrorCsv.ArchivoVacio,
            ErrorCsv.NoEsUtf8,
            ErrorCsv.EncabezadoInvalido,
            ErrorCsv.DemasiadasFilas(500),
            ErrorCsv.DemasiadosEstudiantes(100),
            ErrorCsv.ColumnasIncorrectas(3, 4),
            ErrorCsv.ValorVacio(3, "tema"),
            ErrorCsv.NoNumerico(3, "aciertos_basico", "a"),
            ErrorCsv.TemaRepetido(3, "E1", "criptografia"),
            ErrorCsv.DemasiadosTemasPorEstudiante("E1", 5),
            ErrorCsv.PuntajeGlobalDistinto("E1"),
        )
        todos.forEach { assertEquals(false, it.aTexto().text().isNullOrBlank()) }
    }
}
