package com.example.nightwatchprotocol.cybermind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantillaCsvTest {

    @Test
    fun laPlantilla_traeElEncabezadoYCincoFilasDeUnSoloEstudiante() {
        val lineas = PlantillaCsv.contenido().trim().lines()
        assertEquals(ValidadorCsv.COLUMNAS.joinToString(","), lineas.first())
        assertEquals(5, lineas.size - 1)
        assertEquals(1, lineas.drop(1).map { it.substringBefore(',') }.distinct().size)
    }

    @Test
    fun laPlantilla_esUnCsvValido_conLosCincoTemas() {
        val r = ValidadorCsv.validar(PlantillaCsv.contenido())
        assertTrue(r.toString(), r is ResultadoValidacionCsv.Valido)
        assertEquals(Tema.entries, (r as ResultadoValidacionCsv.Valido).filas.map { it.tema })
    }

    @Test
    fun elPuntajeGlobal_eselPromedioDeLosCincoPuntajesDeTema() {
        val filas = (ValidadorCsv.validar(PlantillaCsv.contenido()) as ResultadoValidacionCsv.Valido).filas
        assertEquals(filas.map { it.puntajeTema }.average(), filas.first().puntajeGlobal, 0.05)
    }

    @Test
    fun laPlantilla_sePuedeEvaluarSinErrores_yNoTrae_decision() {
        assertTrue(!PlantillaCsv.contenido().contains("decision"))
    }
}
