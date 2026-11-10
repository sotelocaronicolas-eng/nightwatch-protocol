package com.example.nightwatchprotocol.cybermind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorManualTest {

    private fun fila(
        tema: Tema? = Tema.CRIPTOGRAFIA,
        exp: Experiencia? = Experiencia.CURSOS,
        b: Int? = 3,
        i: Int? = 1,
        a: Int? = 0,
        t: String = "38",
    ) = FilaManual(tema, exp, b, i, a, t)

    @Test
    fun puntajeTema_seCalculaDesdeLosAciertos() {
        assertEquals(0.0, ReglasCyberMind.calcularPuntajeTema(0, 0, 0), 0.0)
        assertEquals(11.1, ReglasCyberMind.calcularPuntajeTema(1, 0, 0), 0.0)
        assertEquals(44.4, ReglasCyberMind.calcularPuntajeTema(3, 1, 0), 0.0)
        assertEquals(55.6, ReglasCyberMind.calcularPuntajeTema(3, 2, 0), 0.0)
        assertEquals(66.7, ReglasCyberMind.calcularPuntajeTema(3, 2, 1), 0.0)
        assertEquals(100.0, ReglasCyberMind.calcularPuntajeTema(3, 3, 3), 0.0)
    }

    @Test
    fun puntajeTema_delBorrador_esNuloSiFaltaUnAcierto() {
        assertNull(fila(a = null).puntajeTema)
        assertEquals(44.4, fila().puntajeTema!!, 0.0)
    }

    @Test
    fun filaCompleta_esValida_yTraeElPuntajeCalculado() {
        val v = ValidadorManual.validar("M00001", listOf(fila()))
        assertTrue(v.esValido)
        val f = v.entrada!!.single()
        assertEquals("M00001", f.idEstudiante)
        assertEquals(44.4, f.puntajeTema, 0.0)
        assertEquals(44.4, f.puntajeGlobal, 0.0)
        assertEquals(38.0, f.tiempoPromedioSeg, 0.0)
    }

    @Test
    fun sinFilas_oConMasDeCinco_esInvalido() {
        assertTrue(ValidadorManual.validar("M1", emptyList()).cantidadInvalida)
        val seis = List(6) { fila() }
        assertTrue(ValidadorManual.validar("M1", seis).cantidadInvalida)
    }

    @Test
    fun cincoTemasDistintos_sonValidos() {
        val filas = Tema.entries.map { fila(tema = it) }
        assertTrue(ValidadorManual.validar("M1", filas).esValido)
    }

    @Test
    fun campoVacio_haceInvalida_yMarcaElCampo() {
        val v = ValidadorManual.validar("M1", listOf(fila(exp = null, a = null, t = "")))
        assertFalse(v.esValido)
        val e = v.filas.single()
        assertEquals(ErrorCampo.Vacio, e.experiencia)
        assertEquals(ErrorCampo.Vacio, e.aciertosAvanzado)
        assertEquals(ErrorCampo.Vacio, e.tiempoPromedioSeg)
        assertNull(e.tema)
    }

    @Test
    fun temaSinElegir_esError() {
        assertEquals(ErrorCampo.Vacio, ValidadorManual.validar("M1", listOf(fila(tema = null))).filas.single().tema)
    }

    @Test
    fun temaRepetido_seSenalaEnLaSegundaFila() {
        val v = ValidadorManual.validar("M1", listOf(fila(), fila()))
        assertFalse(v.esValido)
        assertNull(v.filas[0].tema)
        assertEquals(ErrorCampo.TemaRepetido, v.filas[1].tema)
    }

    @Test
    fun tiempo_limitesDe4A180() {
        assertTrue(ValidadorManual.validar("M1", listOf(fila(t = "3.9"))).filas.single().tiempoPromedioSeg is ErrorCampo.FueraDeRango)
        assertTrue(ValidadorManual.validar("M1", listOf(fila(t = "180.1"))).filas.single().tiempoPromedioSeg is ErrorCampo.FueraDeRango)
        assertTrue(ValidadorManual.validar("M1", listOf(fila(t = "4"))).esValido)
        assertTrue(ValidadorManual.validar("M1", listOf(fila(t = "180"))).esValido)
    }

    @Test
    fun decimalConComa_seAcepta() {
        val v = ValidadorManual.validar("M1", listOf(fila(t = "38,2")))
        assertTrue(v.esValido)
        assertEquals(38.2, v.entrada!!.single().tiempoPromedioSeg, 0.0)
    }

    @Test
    fun aciertoFueraDeRango_esError() {
        assertTrue(ValidadorManual.validar("M1", listOf(fila(b = 4))).filas.single().aciertosBasico is ErrorCampo.FueraDeRango)
    }

    @Test
    fun experienciaDistintaEntreFilas_esErrorGlobal() {
        val v = ValidadorManual.validar("M1", listOf(fila(), fila(tema = Tema.SEGURIDAD_WEB, exp = Experiencia.LABORAL)))
        assertFalse(v.esValido)
        assertTrue(v.experienciaDistinta)
    }

    @Test
    fun puntajeGlobal_sinTemasCompletos_esNulo() {
        assertNull(ValidadorManual.calcularPuntajeGlobal(emptyList()))
        assertNull(ValidadorManual.calcularPuntajeGlobal(listOf(fila(a = null), FilaManual())))
    }

    @Test
    fun puntajeGlobal_esElPromedioDeLosPuntajesDeTema_aUnDecimal() {
        // 44.4 y 100.0 -> 72.2; 44.4, 55.6 y 66.7 -> 55.6 (55.566...)
        assertEquals(44.4, ValidadorManual.calcularPuntajeGlobal(listOf(fila()))!!, 0.0)
        assertEquals(72.2, ValidadorManual.calcularPuntajeGlobal(listOf(fila(), fila(b = 3, i = 3, a = 3)))!!, 0.0)
        val tres = listOf(fila(b = 3, i = 1, a = 0), fila(b = 3, i = 2, a = 0), fila(b = 3, i = 2, a = 1))
        assertEquals(55.6, ValidadorManual.calcularPuntajeGlobal(tres)!!, 0.0)
    }

    @Test
    fun puntajeGlobal_seRecalculaAlCambiarLosAciertos_yIgnoraLasFilasIncompletas() {
        val antes = listOf(fila(), fila(tema = Tema.SEGURIDAD_WEB, b = 0, i = 0, a = 0))
        val despues = listOf(fila(), fila(tema = Tema.SEGURIDAD_WEB, b = 3, i = 3, a = 3))
        assertEquals(22.2, ValidadorManual.calcularPuntajeGlobal(antes)!!, 0.0)
        assertEquals(72.2, ValidadorManual.calcularPuntajeGlobal(despues)!!, 0.0)
        assertEquals(44.4, ValidadorManual.calcularPuntajeGlobal(listOf(fila(), fila(tema = Tema.SEGURIDAD_WEB, a = null)))!!, 0.0)
    }

    @Test
    fun puntajeGlobal_esUnaSolaCifraParaTodasLasFilas() {
        val v = ValidadorManual.validar("M1", listOf(fila(), fila(tema = Tema.SEGURIDAD_WEB, b = 3, i = 3, a = 3)))
        assertTrue(v.esValido)
        assertEquals(setOf(72.2), v.entrada!!.map { it.puntajeGlobal }.toSet())
    }
}
