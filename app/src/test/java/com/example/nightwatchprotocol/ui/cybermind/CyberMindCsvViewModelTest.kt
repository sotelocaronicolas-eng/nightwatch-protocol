package com.example.nightwatchprotocol.ui.cybermind

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.ArchivosCsv
import com.example.nightwatchprotocol.cybermind.ErrorCsv
import com.example.nightwatchprotocol.cybermind.FakeEvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.InfoArchivo
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.PlantillaCsv
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.data.common.UiText
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeArchivosCsv : ArchivosCsv {
    var info = InfoArchivo("estudiantes_demo.csv", null)
    var contenido: ByteArray = ByteArray(0)
    var falla = false
    var lecturas = 0
    var escrito: Pair<String, String>? = null

    override fun info(uri: String) = info

    override fun leer(uri: String, maxBytes: Long): ByteArray {
        lecturas++
        if (falla) throw IOException("fallo simulado")
        return contenido.take(maxBytes.toInt()).toByteArray()
    }

    override fun escribir(uri: String, texto: String) {
        if (falla) throw IOException("fallo simulado")
        escrito = uri to texto
    }
}

class CyberMindCsvViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val evaluador = FakeEvaluadorEstudiante()
    private val resultados = ResultadosEnMemoria()
    private val archivos = FakeArchivosCsv()

    private val demo: ByteArray by lazy { checkNotNull(javaClass.getResourceAsStream("/estudiantes_demo.csv")).use { it.readBytes() } }

    private fun viewModel() = CyberMindCsvViewModel(evaluador, resultados, archivos, Dispatchers.Unconfined)

    @Test
    fun alInicio_noHayArchivo_yNoSePuedeEvaluar() {
        val state = viewModel().uiState.value
        assertNull(state.archivo)
        assertFalse(state.puedeEvaluar)
    }

    @Test
    fun csvValido_muestraConteos_yPermiteEvaluar() = runTest {
        archivos.contenido = demo
        val vm = viewModel()
        vm.onArchivoElegido("content://demo")
        advanceUntilIdle()
        val archivo = vm.uiState.value.archivo!!
        assertTrue(archivo.valido)
        assertEquals(500, archivo.filas)
        assertEquals(100, archivo.estudiantes)
        assertEquals("estudiantes_demo.csv", archivo.nombre)
        assertTrue(vm.uiState.value.errores.isEmpty())
        assertTrue(vm.uiState.value.puedeEvaluar)
        assertFalse(vm.uiState.value.leyendo)
    }

    @Test
    fun csvInvalido_guardaTodosLosErrores_yNoSePuedeEvaluar() = runTest {
        archivos.contenido = ("id_estudiante,tema,decision\nE1,x,AVANCE\n").toByteArray()
        val vm = viewModel()
        vm.onArchivoElegido("content://malo")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.archivo!!.valido)
        assertTrue(vm.uiState.value.errores.contains(ErrorCsv.ColumnaNoPermitida("decision")))
        assertFalse(vm.uiState.value.puedeEvaluar)
    }

    @Test
    fun archivoDeMasDe200KB_seRechazaSinLeerlo() = runTest {
        archivos.info = InfoArchivo("grande.csv", 200L * 1024L + 1)
        val vm = viewModel()
        vm.onArchivoElegido("content://grande")
        advanceUntilIdle()
        assertEquals(0, archivos.lecturas)
        assertEquals(listOf<ErrorCsv>(ErrorCsv.ArchivoDemasiadoGrande(200L * 1024L + 1)), vm.uiState.value.errores)
        assertFalse(vm.uiState.value.puedeEvaluar)
    }

    @Test
    fun siElTamanoNoSeInforma_seLeeUnByteDeMasParaDetectarElExceso() = runTest {
        archivos.contenido = ByteArray(300 * 1024) { 'a'.code.toByte() }
        val vm = viewModel()
        vm.onArchivoElegido("content://sin-tamano")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errores.single() is ErrorCsv.ArchivoDemasiadoGrande)
    }

    @Test
    fun siLaLecturaFalla_muestraUnError_sinArchivo() = runTest {
        archivos.falla = true
        val vm = viewModel()
        vm.onArchivoElegido("content://roto")
        advanceUntilIdle()
        assertNull(vm.uiState.value.archivo)
        assertEquals(UiText.of(R.string.cybermind_csv_error_read), vm.uiState.value.error)
        assertFalse(vm.uiState.value.leyendo)
    }

    @Test
    fun cerrarElSelectorSinElegir_noCambiaNada() = runTest {
        archivos.contenido = demo
        val vm = viewModel()
        vm.onArchivoElegido("content://demo")
        advanceUntilIdle()
        vm.onArchivoElegido(null)
        assertTrue(vm.uiState.value.archivo != null)
    }

    @Test
    fun quitarElArchivo_limpiaTodo() = runTest {
        archivos.contenido = demo
        val vm = viewModel()
        vm.onArchivoElegido("content://demo")
        advanceUntilIdle()
        vm.onQuitarArchivo()
        assertNull(vm.uiState.value.archivo)
        assertFalse(vm.uiState.value.puedeEvaluar)
        vm.onEvaluar()
        advanceUntilIdle()
        assertNull(resultados.actual.value)
    }

    @Test
    fun evaluar_publicaCienEstudiantes_yAvisaParaNavegar() = runTest {
        archivos.contenido = demo
        val vm = viewModel()
        vm.onArchivoElegido("content://demo")
        advanceUntilIdle()
        vm.events.test {
            vm.onEvaluar()
            advanceUntilIdle()
            assertEquals(CyberMindCsvEvent.VerResultados, awaitItem())
        }
        val sesion = resultados.actual.value!!
        assertEquals(OrigenEntrada.CSV, sesion.origen)
        assertEquals(100, sesion.estudiantes.size)
        assertEquals(500, evaluador.recibidas.size)
    }

    @Test
    fun siElEvaluadorFalla_muestraUnError_yNoNavega() = runTest {
        archivos.contenido = demo
        evaluador.falla = true
        val vm = viewModel()
        vm.onArchivoElegido("content://demo")
        advanceUntilIdle()
        vm.events.test {
            vm.onEvaluar()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(UiText.of(R.string.cybermind_error_evaluate), vm.uiState.value.error)
        assertFalse(vm.uiState.value.evaluando)
        assertNull(resultados.actual.value)
    }

    @Test
    fun plantilla_seEscribeEnElDestinoElegido() = runTest {
        val vm = viewModel()
        vm.onPlantillaDestino("content://destino")
        advanceUntilIdle()
        assertEquals("content://destino" to PlantillaCsv.contenido(), archivos.escrito)
        assertEquals(AvisoPlantilla.GUARDADA, vm.uiState.value.avisoPlantilla)
    }

    @Test
    fun plantilla_siFalla_avisa_yAlCancelarNoHaceNada() = runTest {
        archivos.falla = true
        val vm = viewModel()
        vm.onPlantillaDestino("content://destino")
        advanceUntilIdle()
        assertEquals(AvisoPlantilla.FALLO, vm.uiState.value.avisoPlantilla)

        val otro = viewModel()
        otro.onPlantillaDestino(null)
        assertNull(otro.uiState.value.avisoPlantilla)
    }
}
