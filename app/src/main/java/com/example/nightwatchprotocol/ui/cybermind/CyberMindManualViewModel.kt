package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.EvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.FilaManual
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.ReglasCyberMind
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.cybermind.ValidacionManual
import com.example.nightwatchprotocol.cybermind.ValidadorManual
import com.example.nightwatchprotocol.cybermind.evaluarEstudiantes
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * El ingreso manual. Los cinco borradores se conservan aunque se baje la cantidad de temas (solo se muestran los primeros
 * [cantidad]), así que subir y bajar el número no borra lo escrito.
 */
@Immutable
data class CyberMindManualUiState(
    val cantidad: Int = ReglasCyberMind.MIN_TEMAS,
    val borradores: List<FilaManual> = List(ReglasCyberMind.MAX_TEMAS) { FilaManual() },
    /** Índice de la fila abierta; las demás se ven plegadas. */
    val expandida: Int = 0,
    val evaluando: Boolean = false,
    val error: UiText? = null,
) {
    val filas: List<FilaManual> get() = borradores.take(cantidad)

    /** La validación de lo que se ve; el ID no importa aquí (se asigna al evaluar). */
    val validacion: ValidacionManual get() = ValidadorManual.validar(ID_PRUEBA, filas)

    val puedeEvaluar: Boolean get() = !evaluando && validacion.esValido

    private companion object {
        const val ID_PRUEBA = "M00000"
    }
}

sealed interface CyberMindManualEvent {
    data object VerResultados : CyberMindManualEvent
}

class CyberMindManualViewModel(
    private val evaluador: EvaluadorEstudiante,
    private val resultados: ResultadosEnMemoria,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CyberMindManualUiState())
    val uiState: StateFlow<CyberMindManualUiState> = _uiState.asStateFlow()

    private val _events = Channel<CyberMindManualEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var contadorId = 0

    fun onCantidadChange(cantidad: Int) {
        if (cantidad !in ReglasCyberMind.MIN_TEMAS..ReglasCyberMind.MAX_TEMAS) return
        _uiState.update { it.copy(cantidad = cantidad, expandida = it.expandida.coerceAtMost(cantidad - 1), error = null) }
    }

    fun onExpandir(indice: Int) {
        _uiState.update { if (indice in 0 until it.cantidad) it.copy(expandida = indice) else it }
    }

    /** Cambia la fila [indice] con [cambio]; `FilaManual` es inmutable, así que se reemplaza entera. */
    fun onFilaChange(indice: Int, cambio: (FilaManual) -> FilaManual) {
        _uiState.update { state ->
            if (indice !in state.borradores.indices) return@update state
            state.copy(
                borradores = state.borradores.mapIndexed { i, fila -> if (i == indice) cambio(fila) else fila },
                error = null,
            )
        }
    }

    fun onEvaluar() {
        val state = _uiState.value
        if (!state.puedeEvaluar) return
        _uiState.update { it.copy(evaluando = true, error = null) }
        val id = "M%05d".format(Locale.ROOT, ++contadorId)
        val entrada = ValidadorManual.validar(id, state.filas).entrada ?: run {
            _uiState.update { it.copy(evaluando = false) }
            return
        }
        viewModelScope.launch {
            runCatchingKeepCancellation { evaluador.evaluarEstudiantes(entrada, dispatcher) }
                .onSuccess {
                    resultados.publicar(OrigenEntrada.MANUAL, it)
                    _uiState.update { s -> s.copy(evaluando = false) }
                    _events.send(CyberMindManualEvent.VerResultados)
                }
                .onFailure {
                    _uiState.update { s -> s.copy(evaluando = false, error = UiText.of(R.string.cybermind_error_evaluate)) }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                CyberMindManualViewModel(application.container.evaluadorEstudiante, application.container.resultadosCyberMind)
            }
        }
    }
}
