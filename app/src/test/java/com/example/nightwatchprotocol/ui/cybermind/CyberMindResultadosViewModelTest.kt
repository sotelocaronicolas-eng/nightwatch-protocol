package com.example.nightwatchprotocol.ui.cybermind

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.cybermind.FakeEvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.MODEL_VERSION
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.OrigenEvaluacion
import com.example.nightwatchprotocol.cybermind.ResultadoEstudiante
import com.example.nightwatchprotocol.cybermind.ResultadoTema
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.cybermind.Tema
import com.example.nightwatchprotocol.cybermind.Veredicto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CyberMindResultadosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val resultados = ResultadosEnMemoria()

    private fun tema(t: Tema, v: Veredicto, origen: OrigenEvaluacion = OrigenEvaluacion.MODELO) =
        ResultadoTema(t, v, 60.0, if (origen == OrigenEvaluacion.MODELO) 0.8f else null, origen)

    private val e1 = ResultadoEstudiante(
        "E1",
        listOf(tema(Tema.CRIPTOGRAFIA, Veredicto.AVANCE), tema(Tema.SEGURIDAD_WEB, Veredicto.REFUERZO)),
    )
    private val e2 = ResultadoEstudiante("E2", listOf(tema(Tema.CRIPTOGRAFIA, Veredicto.REFUERZO)))

    @Test
    fun elResumen_cuentaTemasNoEstudiantes() = runTest {
        resultados.publicar(OrigenEntrada.CSV, listOf(e1, e2))
        val vm = CyberMindResultadosViewModel(resultados)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.estudiantes.size)
            assertEquals(1, state.avances)
            assertEquals(2, state.refuerzos)
            assertEquals(OrigenEntrada.CSV, state.origen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sinSesion_noHayEstudiantes() {
        val state = CyberMindResultadosViewModel(resultados).uiState.value
        assertTrue(state.estudiantes.isEmpty())
        assertNull(state.origen)
    }

    @Test
    fun alSalir_seBorranLosDatosDeLaMemoria() {
        resultados.publicar(OrigenEntrada.MANUAL, listOf(e1))
        val vm = CyberMindResultadosViewModel(resultados)
        // onCleared es protegido: lo llama el ViewModelStore al cerrarse la pantalla.
        vm.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
        assertNull(resultados.actual.value)
    }

    @Test
    fun detalle_traeAlEstudianteDeLaRuta_yLaVersionDelModelo() {
        resultados.publicar(OrigenEntrada.CSV, listOf(e1, e2))
        val state = CyberMindDetalleViewModel("E2", resultados, FakeEvaluadorEstudiante(versionEmbebida = "7")).uiState.value
        assertEquals(e2, state.estudiante)
        assertEquals(MODEL_VERSION, state.versionModelo)
        assertEquals("7", state.versionEmbebida)
    }

    @Test
    fun detalle_conUnIdQueNoEsta_noTraeEstudiante() {
        resultados.publicar(OrigenEntrada.CSV, listOf(e1))
        assertNull(CyberMindDetalleViewModel("E9", resultados, FakeEvaluadorEstudiante()).uiState.value.estudiante)
        assertNull(CyberMindDetalleViewModel("E1", ResultadosEnMemoria(), FakeEvaluadorEstudiante()).uiState.value.estudiante)
    }

    @Test
    fun origenDelEstudiante_conRegla_diceRespaldo() {
        val e = ResultadoEstudiante("E3", listOf(tema(Tema.CRIPTOGRAFIA, Veredicto.AVANCE, OrigenEvaluacion.REGLA_RESPALDO)))
        assertEquals(OrigenEvaluacion.REGLA_RESPALDO, e.origen)
    }
}
