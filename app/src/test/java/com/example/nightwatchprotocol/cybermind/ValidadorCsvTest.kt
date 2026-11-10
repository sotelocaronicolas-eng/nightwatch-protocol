package com.example.nightwatchprotocol.cybermind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorCsvTest {

    private val encabezado = ValidadorCsv.COLUMNAS.joinToString(",")

    private fun fila(
        id: String = "E00001",
        tema: String = "criptografia",
        exp: String = "cursos",
        b: String = "3",
        i: String = "2",
        a: String = "1",
        pt: String = "66.7",
        pg: String = "70.0",
        t: String = "30.5",
    ) = listOf(id, tema, exp, b, i, a, pt, pg, t).joinToString(",")

    private fun csv(vararg filas: String, cabecera: String = encabezado) = (listOf(cabecera) + filas).joinToString("\n")

    private fun errores(texto: String): List<ErrorCsv> {
        val r = ValidadorCsv.validar(texto)
        assertTrue("se esperaba un CSV inválido: $r", r is ResultadoValidacionCsv.Invalido)
        return (r as ResultadoValidacionCsv.Invalido).errores
    }

    @Test
    fun csvDemo_esValido_con500FilasY100Estudiantes() {
        val texto = checkNotNull(javaClass.getResourceAsStream("/estudiantes_demo.csv")).bufferedReader().use { it.readText() }
        val r = ValidadorCsv.validar(texto)
        assertTrue(r.toString(), r is ResultadoValidacionCsv.Valido)
        val filas = (r as ResultadoValidacionCsv.Valido).filas
        assertEquals(500, filas.size)
        assertEquals(100, filas.map { it.idEstudiante }.distinct().size)
    }

    @Test
    fun csvValido_aceptaFinesDeLineaWindowsYBom() {
        val texto = Char(0xFEFF).toString() + csv(fila(), fila(tema = "seguridad_web")).replace("\n", "\r\n") + "\r\n"
        assertTrue(ValidadorCsv.validar(texto) is ResultadoValidacionCsv.Valido)
    }

    @Test
    fun archivoVacio_esInvalido() {
        assertEquals(listOf(ErrorCsv.ArchivoVacio), errores(""))
    }

    @Test
    fun soloEncabezado_esValidoSinFilas() {
        assertEquals(emptyList<FilaEstudiante>(), (ValidadorCsv.validar(csv()) as ResultadoValidacionCsv.Valido).filas)
    }

    @Test
    fun columnaDecision_noSePermite() {
        val e = errores(csv(cabecera = "$encabezado,decision"))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ColumnaNoPermitida("decision")), e)
    }

    @Test
    fun otraColumna_noSePermite() {
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ColumnaNoPermitida("extra")), errores(csv(cabecera = "$encabezado,extra")))
    }

    @Test
    fun encabezadoEnOtroOrden_esInvalido() {
        val cabecera = ValidadorCsv.COLUMNAS.reversed().joinToString(",")
        assertEquals(listOf<ErrorCsv>(ErrorCsv.EncabezadoInvalido), errores(csv(cabecera = cabecera)))
    }

    @Test
    fun encabezadoIncompleto_esInvalido() {
        val cabecera = ValidadorCsv.COLUMNAS.dropLast(1).joinToString(",")
        assertEquals(listOf<ErrorCsv>(ErrorCsv.EncabezadoInvalido), errores(csv(cabecera = cabecera)))
    }

    @Test
    fun filaConMenosColumnas_esInvalida() {
        val e = errores(csv("E00001,criptografia,cursos"))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ColumnasIncorrectas(2, 3)), e)
    }

    @Test
    fun valorVacio_esInvalido() {
        val e = errores(csv(fila(exp = "")))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ValorVacio(2, "experiencia_previa")), e)
    }

    @Test
    fun temaFueraDeLaLista_esInvalido() {
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ValorNoPermitido(2, "tema", "Criptografía")), errores(csv(fila(tema = "Criptografía"))))
    }

    @Test
    fun experienciaFueraDeLaLista_esInvalida() {
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ValorNoPermitido(2, "experiencia_previa", "experto")), errores(csv(fila(exp = "experto"))))
    }

    @Test
    fun aciertoFueraDeRango_esInvalido() {
        assertTrue(errores(csv(fila(b = "4"))).contains(ErrorCsv.FueraDeRango(2, "aciertos_basico", "4")))
    }

    @Test
    fun aciertoNegativoOConDecimal_noEsNumeroValido() {
        assertTrue(errores(csv(fila(b = "-1"))).contains(ErrorCsv.NoNumerico(2, "aciertos_basico", "-1")))
        assertTrue(errores(csv(fila(i = "1.5"))).contains(ErrorCsv.NoNumerico(2, "aciertos_intermedio", "1.5")))
    }

    @Test
    fun puntajeFueraDeRango_esInvalido() {
        assertTrue(errores(csv(fila(pt = "100.5"))).contains(ErrorCsv.FueraDeRango(2, "puntaje_tema", "100.5")))
        assertTrue(errores(csv(fila(pg = "101"))).contains(ErrorCsv.FueraDeRango(2, "puntaje_global", "101")))
    }

    @Test
    fun tiempoFueraDeRango_esInvalido() {
        assertTrue(errores(csv(fila(t = "3.9"))).contains(ErrorCsv.FueraDeRango(2, "tiempo_promedio_seg", "3.9")))
        assertTrue(errores(csv(fila(t = "180.1"))).contains(ErrorCsv.FueraDeRango(2, "tiempo_promedio_seg", "180.1")))
    }

    @Test
    fun tiempoEnLosLimites_esValido() {
        assertTrue(ValidadorCsv.validar(csv(fila(t = "4"), fila(tema = "seguridad_web", t = "180"))) is ResultadoValidacionCsv.Valido)
    }

    @Test
    fun decimalConComa_esInvalido() {
        assertTrue(errores(csv(fila(t = "30,5"))).any { it is ErrorCsv.NoNumerico || it is ErrorCsv.ColumnasIncorrectas })
    }

    @Test
    fun puntajeTemaIncoherente_esInvalido() {
        val e = errores(csv(fila(pt = "70.0")))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.PuntajeTemaIncoherente(2, 66.7, 70.0)), e)
    }

    @Test
    fun puntajeTema_dentroDeLaTolerancia_esValido() {
        assertTrue(ValidadorCsv.validar(csv(fila(pt = "66.75"))) is ResultadoValidacionCsv.Valido)
        assertTrue(errores(csv(fila(pt = "66.8"))).isNotEmpty())
    }

    @Test
    fun temaRepetidoDelMismoEstudiante_seSenalaEnLaSegundaLinea() {
        val e = errores(csv(fila(), fila()))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.TemaRepetido(3, "E00001", "criptografia")), e)
    }

    @Test
    fun mismoTemaEnEstudiantesDistintos_esValido() {
        assertTrue(ValidadorCsv.validar(csv(fila(id = "E1"), fila(id = "E2"))) is ResultadoValidacionCsv.Valido)
    }

    @Test
    fun masDeCincoFilasPorEstudiante_esInvalido() {
        val e = errores(csv(*Array(6) { fila() }))
        assertTrue(e.contains(ErrorCsv.DemasiadosTemasPorEstudiante("E00001", 5)))
    }

    @Test
    fun experienciaDistintaEntreFilas_usaElMensajeDelEstudiante() {
        val e = errores(csv(fila(id = "E00012"), fila(id = "E00012", tema = "seguridad_web", exp = "laboral")))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ExperienciaDistinta("E00012")), e)
    }

    @Test
    fun puntajeGlobalDistintoEntreFilas_esInvalido() {
        val e = errores(csv(fila(id = "E00012"), fila(id = "E00012", tema = "seguridad_web", pg = "71.0")))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.PuntajeGlobalDistinto("E00012")), e)
    }

    @Test
    fun puntajeGlobalIgualConOtroFormato_esConsistente() {
        assertTrue(
            ValidadorCsv.validar(csv(fila(pg = "70"), fila(tema = "seguridad_web", pg = "70.00"))) is ResultadoValidacionCsv.Valido,
        )
    }

    private val cincoTemas = listOf("fundamentos_redes", "criptografia", "seguridad_web", "ingenieria_social", "gestion_vulnerabilidades")

    /** Cinco filas de un estudiante con `puntaje_tema` 66.7 cada una (promedio 66.7) y el `puntaje_global` dado. */
    private fun cincoFilas(id: String = "E00012", pg: String) = cincoTemas.map { fila(id = id, tema = it, pg = pg) }.toTypedArray()

    @Test
    fun conCincoFilas_elPuntajeGlobalIgualAlPromedio_esValido() {
        assertTrue(ValidadorCsv.validar(csv(*cincoFilas(pg = "66.7"))) is ResultadoValidacionCsv.Valido)
    }

    @Test
    fun conCincoFilas_dentroDeLaToleranciaDe0coma1_esValido() {
        assertTrue(ValidadorCsv.validar(csv(*cincoFilas(pg = "66.8"))) is ResultadoValidacionCsv.Valido)
        assertTrue(ValidadorCsv.validar(csv(*cincoFilas(pg = "66.6"))) is ResultadoValidacionCsv.Valido)
    }

    @Test
    fun conCincoFilas_fueraDeLaTolerancia_esInvalido() {
        assertEquals(
            listOf<ErrorCsv>(ErrorCsv.PuntajeGlobalNoPromedio("E00012")),
            errores(csv(*cincoFilas(pg = "66.9"))),
        )
        assertEquals(
            listOf<ErrorCsv>(ErrorCsv.PuntajeGlobalNoPromedio("E00012")),
            errores(csv(*cincoFilas(pg = "70.0"))),
        )
    }

    @Test
    fun conMenosDeCincoFilas_noSeComparaConElPromedio_soloSeExigeQueSeaIgual() {
        val cuatro = cincoTemas.take(4).map { fila(id = "E00012", tema = it, pg = "99.0") }
        assertTrue(ValidadorCsv.validar(csv(*cuatro.toTypedArray())) is ResultadoValidacionCsv.Valido)
        val distinto = errores(csv(fila(id = "E00012", pg = "99.0"), fila(id = "E00012", tema = "seguridad_web", pg = "98.0")))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.PuntajeGlobalDistinto("E00012")), distinto)
    }

    @Test
    fun conCincoFilas_yGlobalDistintoEntreFilas_soloSeInformaLaDiferencia() {
        val filas = cincoTemas.mapIndexed { i, t -> fila(id = "E00012", tema = t, pg = if (i == 0) "60.0" else "66.7") }
        assertEquals(listOf<ErrorCsv>(ErrorCsv.PuntajeGlobalDistinto("E00012")), errores(csv(*filas.toTypedArray())))
    }

    @Test
    fun quinientasFilas_sonValidas_yQuinientasUna_seRechaza() {
        val cinco = cincoTemas
        val filas = (0 until 100).flatMap { s -> cinco.map { fila(id = "E%05d".format(s), tema = it, pg = "66.7") } }
        assertEquals(500, filas.size)
        assertTrue(ValidadorCsv.validar(csv(*filas.toTypedArray())) is ResultadoValidacionCsv.Valido)

        val e = errores(csv(*(filas + fila(id = "E00000")).toTypedArray()))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.DemasiadasFilas(500)), e)
    }

    @Test
    fun cienEstudiantes_sonValidos_yCientoUno_seRechaza() {
        val cien = (0 until 100).map { fila(id = "E%05d".format(it)) }
        assertTrue(ValidadorCsv.validar(csv(*cien.toTypedArray())) is ResultadoValidacionCsv.Valido)

        val e = errores(csv(*(cien + fila(id = "E00100")).toTypedArray()))
        assertEquals(listOf<ErrorCsv>(ErrorCsv.DemasiadosEstudiantes(100)), e)
    }

    @Test
    fun tamanoMaximo_200KB_seComprueba() {
        assertNull(ValidadorCsv.validarTamano(200L * 1024L))
        assertEquals(ErrorCsv.ArchivoDemasiadoGrande(200L * 1024L + 1), ValidadorCsv.validarTamano(200L * 1024L + 1))

        val grande = "x".repeat(200 * 1024 + 1)
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ArchivoDemasiadoGrande(200L * 1024L + 1)), errores(grande))
    }

    @Test
    fun tamanoSeMideEnBytesNoEnCaracteres() {
        val conTildes = "é".repeat(110 * 1024) // 110 K caracteres, 220 KB en UTF-8
        assertTrue(errores(conTildes).single() is ErrorCsv.ArchivoDemasiadoGrande)
    }

    @Test
    fun variosErrores_seAcumulanTodos() {
        val e = errores(csv(fila(b = "9"), fila(tema = "x"), fila(exp = "")))
        assertTrue(e.size >= 3)
    }
}
