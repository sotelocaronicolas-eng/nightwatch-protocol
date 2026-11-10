package com.example.nightwatchprotocol.ui.cybermind

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.Experiencia
import com.example.nightwatchprotocol.cybermind.FakeEvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.FilaManual
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.cybermind.Tema
import com.example.nightwatchprotocol.data.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CyberMindManualViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val evaluador = FakeEvaluadorEstudiante()
    private val resultados = ResultadosEnMemoria()

    private fun viewModel() = CyberMindManualViewModel(evaluador, resultados, Dispatchers.Unconfined)

    private fun completa(tema: Tema = Tema.CRIPTOGRAFIA) =
        FilaManual(tema, Experiencia.CURSOS, 3, 1, 0, "61.0", "38")

    @Test
    fun alInicio_hayUnTema_yNoSePuedeEvaluar() {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(1, state.cantidad)
        assertEquals(1, state.filas.size)
        assertFalse(state.puedeEvaluar)
    }

    @Test
    fun conTodoValido_sePuedeEvaluar() {
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
        assertTrue(vm.uiState.value.puedeEvaluar)
    }

    @Test
    fun cambiarLaCantidad_muestraEsasFilas_yConservaLoEscrito() {
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
        vm.onCantidadChange(3)
        assertEquals(3, vm.uiState.value.filas.size)
        assertFalse(vm.uiState.value.puedeEvaluar) // las filas nuevas están vacías
        vm.onCantidadChange(1)
        assertEquals(Tema.CRIPTOGRAFIA, vm.uiState.value.filas.single().tema)
    }

    @Test
    fun cantidadFueraDeRango_seIgnora() {
        val vm = viewModel()
        vm.onCantidadChange(0)
        vm.onCantidadChange(6)
        assertEquals(1, vm.uiState.value.cantidad)
    }

    @Test
    fun bajarLaCantidad_cierraLaFilaQueYaNoExiste() {
        val vm = viewModel()
        vm.onCantidadChange(4)
        vm.onExpandir(3)
        vm.onCantidadChange(2)
        assertEquals(1, vm.uiState.value.expandida)
    }

    @Test
    fun puntajeTema_seCalculaSoloDesdeLosAciertos() {
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
        assertEquals(44.4, vm.uiState.value.filas.single().puntajeTema!!, 0.0)
    }

    @Test
    fun temaRepetido_impideEvaluar() {
        val vm = viewModel()
        vm.onCantidadChange(2)
        vm.onFilaChange(0) { completa() }
        vm.onFilaChange(1) { completa() }
        assertFalse(vm.uiState.value.puedeEvaluar)
    }

    @Test
    fun experienciaDistinta_impideEvaluar_yMuestraElAviso() {
        val vm = viewModel()
        vm.onCantidadChange(2)
        vm.onFilaChange(0) { completa() }
        vm.onFilaChange(1) { completa(Tema.SEGURIDAD_WEB).copy(experiencia = Experiencia.LABORAL) }
        assertFalse(vm.uiState.value.puedeEvaluar)
        assertTrue(vm.uiState.value.validacion.experienciaDistinta)
    }

    @Test
    fun evaluar_publicaLosResultados_yAvisaParaNavegar() = runTest {
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
        vm.events.test {
            vm.onEvaluar()
            advanceUntilIdle()
            assertEquals(com.example.nightwatchprotocol.ui.cybermind.CyberMindManualEvent.VerResultados, awaitItem())
        }
        val sesion = resultados.actual.value!!
        assertEquals(OrigenEntrada.MANUAL, sesion.origen)
        assertEquals("M00001", sesion.estudiantes.single().idEstudiante)
        assertEquals(1, sesion.estudiantes.single().refuerzos)
        assertFalse(vm.uiState.value.evaluando)
    }

    @Test
    fun evaluar_sinDatosValidos_noHaceNada() = runTest {
        val vm = viewModel()
        vm.onEvaluar()
        advanceUntilIdle()
        assertNull(resultados.actual.value)
        assertTrue(evaluador.recibidas.isEmpty())
    }

    @Test
    fun siElEvaluadorFalla_muestraUnError_yNoNavega() = runTest {
        evaluador.falla = true
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
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
    fun editarDespuesDeUnError_loLimpia() = runTest {
        evaluador.falla = true
        val vm = viewModel()
        vm.onFilaChange(0) { completa() }
        vm.onEvaluar()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
        vm.onFilaChange(0) { it.copy(tiempoPromedioSeg = "40") }
        assertNull(vm.uiState.value.error)
    }
}
