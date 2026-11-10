package com.example.nightwatchprotocol.cybermind

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluarEstudiantesTest {

    private fun fila(id: String, tema: Tema, b: Int, i: Int, a: Int) = FilaEstudiante(
        id, tema, Experiencia.CURSOS, b, i, a, ReglasCyberMind.calcularPuntajeTema(b, i, a), 60.0, 30.0,
    )

    @Test
    fun agrupaPorEstudiante_enElOrdenDelArchivo() = runTest {
        val filas = listOf(
            fila("E2", Tema.CRIPTOGRAFIA, 3, 3, 3),
            fila("E1", Tema.CRIPTOGRAFIA, 0, 0, 0),
            fila("E2", Tema.SEGURIDAD_WEB, 1, 0, 0),
        )
        val r = FakeEvaluadorEstudiante().evaluarEstudiantes(filas, Dispatchers.Unconfined)

        assertEquals(listOf("E2", "E1"), r.map { it.idEstudiante })
        assertEquals(2, r[0].temas.size)
        assertEquals(1, r[0].avances)
        assertEquals(1, r[0].refuerzos)
        assertEquals(1, r[1].refuerzos)
    }

    @Test
    fun origen_delEstudiante_esRespaldoSiAlgunTemaLoEs() {
        val modelo = ResultadoTema(Tema.CRIPTOGRAFIA, Veredicto.AVANCE, 80.0, 0.9f, OrigenEvaluacion.MODELO)
        val respaldo = modelo.copy(tema = Tema.SEGURIDAD_WEB, origen = OrigenEvaluacion.REGLA_RESPALDO, confianza = null)
        assertEquals(OrigenEvaluacion.MODELO, ResultadoEstudiante("E1", listOf(modelo)).origen)
        assertEquals(OrigenEvaluacion.REGLA_RESPALDO, ResultadoEstudiante("E1", listOf(modelo, respaldo)).origen)
    }

    @Test
    fun siElModeloNoSePuedeCrear_conUnError_seUsaLaReglaDel70_yNoSePropaga() = runTest {
        var intentos = 0
        // UnsatisfiedLinkError es un Error, no una Exception: la librería nativa de ONNX no está en esta arquitectura.
        val evaluador = OnnxEvaluadorEstudiante {
            intentos++
            throw UnsatisfiedLinkError("libonnxruntime.so no está en esta arquitectura")
        }
        val filas = listOf(
            fila("E1", Tema.CRIPTOGRAFIA, 3, 1, 0), // 44,4 -> REFUERZO
            fila("E1", Tema.SEGURIDAD_WEB, 3, 3, 3), // 100 -> AVANCE
        )

        val r = evaluador.evaluarEstudiantes(filas, Dispatchers.Unconfined).single()

        assertEquals(listOf(Veredicto.REFUERZO, Veredicto.AVANCE), r.temas.map { it.decision })
        assertTrue(r.temas.all { it.origen == OrigenEvaluacion.REGLA_RESPALDO && it.confianza == null })
        assertEquals(OrigenEvaluacion.REGLA_RESPALDO, r.origen)
        assertEquals(null, evaluador.versionEmbebida)
        assertEquals("el fallo se recuerda: no se reintenta por cada fila", 1, intentos)
    }

    @Test
    fun csvDemo_seEvaluaCompleto() = runTest {
        val texto = checkNotNull(javaClass.getResourceAsStream("/estudiantes_demo.csv")).bufferedReader().use { it.readText() }
        val filas = (ValidadorCsv.validar(texto) as ResultadoValidacionCsv.Valido).filas
        val r = FakeEvaluadorEstudiante().evaluarEstudiantes(filas, Dispatchers.Unconfined)

        assertEquals(100, r.size)
        assertEquals(500, r.sumOf { it.temas.size })
        assertTrue(r.all { it.avances + it.refuerzos == it.temas.size })
    }
}
