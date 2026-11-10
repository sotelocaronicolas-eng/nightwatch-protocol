package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.cybermind.EvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.MODEL_VERSION
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.ResultadoEstudiante
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.cybermind.SesionResultados
import com.example.nightwatchprotocol.ui.navigation.CyberMindDetalleRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** La lista de estudiantes con el resumen de arriba. Sin sesión (por ejemplo tras reiniciar la app) no hay nada que mostrar. */
@Immutable
data class CyberMindResultadosUiState(val sesion: SesionResultados? = null) {
    val estudiantes: List<ResultadoEstudiante> get() = sesion?.estudiantes.orEmpty()
    val origen: OrigenEntrada? get() = sesion?.origen

    /** Los números del resumen cuentan temas evaluados, no estudiantes. */
    val avances: Int get() = estudiantes.sumOf { it.avances }
    val refuerzos: Int get() = estudiantes.sumOf { it.refuerzos }
}

class CyberMindResultadosViewModel(private val resultados: ResultadosEnMemoria) : ViewModel() {

    val uiState: StateFlow<CyberMindResultadosUiState> = resultados.actual
        .map { CyberMindResultadosUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CyberMindResultadosUiState(resultados.actual.value))

    /** Al salir de los resultados no queda nada de los estudiantes en memoria. */
    override fun onCleared() {
        resultados.limpiar()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                CyberMindResultadosViewModel(application.container.resultadosCyberMind)
            }
        }
    }
}

@Immutable
data class CyberMindDetalleUiState(
    /** `null` si ya no está en memoria (la app se reinició con el detalle abierto): la pantalla se cierra. */
    val estudiante: ResultadoEstudiante?,
    val versionModelo: String = MODEL_VERSION,
    /** La versión guardada dentro del .onnx, si el modelo cargó y la trae. */
    val versionEmbebida: String? = null,
)

class CyberMindDetalleViewModel(
    idEstudiante: String,
    resultados: ResultadosEnMemoria,
    evaluador: EvaluadorEstudiante,
) : ViewModel() {

    val uiState: StateFlow<CyberMindDetalleUiState> = MutableStateFlow(
        CyberMindDetalleUiState(
            estudiante = resultados.actual.value?.estudiantes?.firstOrNull { it.idEstudiante == idEstudiante },
            versionEmbebida = runCatching { evaluador.versionEmbebida }.getOrNull(),
        ),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val saved: SavedStateHandle = createSavedStateHandle()
                val ruta = saved.toRoute<CyberMindDetalleRoute>()
                CyberMindDetalleViewModel(
                    ruta.idEstudiante,
                    application.container.resultadosCyberMind,
                    application.container.evaluadorEstudiante,
                )
            }
        }
    }
}
