package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.ArchivosCsv
import com.example.nightwatchprotocol.cybermind.ErrorCsv
import com.example.nightwatchprotocol.cybermind.EvaluadorEstudiante
import com.example.nightwatchprotocol.cybermind.FilaEstudiante
import com.example.nightwatchprotocol.cybermind.LecturaCsv
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.PlantillaCsv
import com.example.nightwatchprotocol.cybermind.ResultadoValidacionCsv
import com.example.nightwatchprotocol.cybermind.ResultadosEnMemoria
import com.example.nightwatchprotocol.cybermind.ValidadorCsv
import com.example.nightwatchprotocol.cybermind.evaluarEstudiantes
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** El archivo elegido y lo que se supo de él al leerlo ([filas] y [estudiantes] son lo que trae, sea válido o no). */
@Immutable
data class ArchivoCsvElegido(
    val nombre: String?,
    val bytes: Long,
    val filas: Int,
    val estudiantes: Int,
    val valido: Boolean,
)

enum class AvisoPlantilla { GUARDADA, FALLO }

@Immutable
data class CyberMindCsvUiState(
    val archivo: ArchivoCsvElegido? = null,
    /** Todos los errores del archivo; la pantalla muestra los primeros [ValidadorCsv.ERRORES_VISIBLES]. */
    val errores: List<ErrorCsv> = emptyList(),
    val leyendo: Boolean = false,
    val evaluando: Boolean = false,
    val avisoPlantilla: AvisoPlantilla? = null,
    val error: UiText? = null,
) {
    val ocupado: Boolean get() = leyendo || evaluando
    val puedeEvaluar: Boolean get() = !ocupado && archivo?.valido == true
}

sealed interface CyberMindCsvEvent {
    data object VerResultados : CyberMindCsvEvent
}

class CyberMindCsvViewModel(
    private val evaluador: EvaluadorEstudiante,
    private val resultados: ResultadosEnMemoria,
    private val archivos: ArchivosCsv,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CyberMindCsvUiState())
    val uiState: StateFlow<CyberMindCsvUiState> = _uiState.asStateFlow()

    private val _events = Channel<CyberMindCsvEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Las filas del archivo válido; solo en memoria y nunca en el estado que ve la pantalla. */
    private var filasValidas: List<FilaEstudiante>? = null
    private var lectura: Job? = null

    private class Leido(val info: ArchivoCsvElegido, val errores: List<ErrorCsv>, val filas: List<FilaEstudiante>?)

    /** [uri] es `null` si la persona cerró el selector sin elegir. */
    fun onArchivoElegido(uri: String?) {
        if (uri == null) return
        lectura?.cancel()
        filasValidas = null
        _uiState.update { it.copy(leyendo = true, archivo = null, errores = emptyList(), error = null) }
        lectura = viewModelScope.launch {
            runCatchingKeepCancellation { withContext(dispatcher) { leer(uri) } }
                .onSuccess { leido ->
                    filasValidas = leido.filas
                    _uiState.update { it.copy(leyendo = false, archivo = leido.info, errores = leido.errores) }
                }
                .onFailure {
                    _uiState.update { it.copy(leyendo = false, error = UiText.of(R.string.cybermind_csv_error_read)) }
                }
        }
    }

    private fun leer(uri: String): Leido {
        val info = archivos.info(uri)
        // El tamaño se comprueba antes de leer: un archivo enorme no se abre.
        info.bytes?.let { tamano ->
            ValidadorCsv.validarTamano(tamano)?.let { return Leido(ArchivoCsvElegido(info.nombre, tamano, 0, 0, false), listOf(it), null) }
        }
        val bytes = archivos.leer(uri, ValidadorCsv.MAX_BYTES + 1)
        val lecturaCsv: LecturaCsv = ValidadorCsv.validarBytes(bytes)
        val tamano = info.bytes ?: bytes.size.toLong()
        return when (val r = lecturaCsv.resultado) {
            is ResultadoValidacionCsv.Valido ->
                Leido(ArchivoCsvElegido(info.nombre, tamano, lecturaCsv.filasDeDatos, lecturaCsv.estudiantes, true), emptyList(), r.filas)
            is ResultadoValidacionCsv.Invalido ->
                Leido(ArchivoCsvElegido(info.nombre, tamano, lecturaCsv.filasDeDatos, lecturaCsv.estudiantes, false), r.errores, null)
        }
    }

    fun onQuitarArchivo() {
        lectura?.cancel()
        filasValidas = null
        _uiState.update { it.copy(archivo = null, errores = emptyList(), leyendo = false, error = null) }
    }

    /** [uri] es el destino que eligió la persona en el selector de "crear documento" (`null` si lo cerró). */
    fun onPlantillaDestino(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            runCatchingKeepCancellation { withContext(dispatcher) { archivos.escribir(uri, PlantillaCsv.contenido()) } }
                .onSuccess { _uiState.update { it.copy(avisoPlantilla = AvisoPlantilla.GUARDADA) } }
                .onFailure { _uiState.update { it.copy(avisoPlantilla = AvisoPlantilla.FALLO) } }
        }
    }

    fun onEvaluar() {
        val filas = filasValidas
        if (!_uiState.value.puedeEvaluar || filas == null) return
        _uiState.update { it.copy(evaluando = true, error = null) }
        viewModelScope.launch {
            runCatchingKeepCancellation { evaluador.evaluarEstudiantes(filas, dispatcher) }
                .onSuccess {
                    resultados.publicar(OrigenEntrada.CSV, it)
                    _uiState.update { s -> s.copy(evaluando = false) }
                    _events.send(CyberMindCsvEvent.VerResultados)
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
                val container = application.container
                CyberMindCsvViewModel(container.evaluadorEstudiante, container.resultadosCyberMind, container.archivosCsv)
            }
        }
    }
}
