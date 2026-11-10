package com.example.nightwatchprotocol.cybermind

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OrigenEntrada { MANUAL, CSV }

data class SesionResultados(val origen: OrigenEntrada, val estudiantes: List<ResultadoEstudiante>)

/**
 * La última evaluación, solo en memoria: la comparten el ingreso manual, el CSV, la lista y el detalle. No se guarda en disco ni
 * se envía a ningún lado; se vacía al salir de los resultados y desaparece con el proceso de la app.
 */
class ResultadosEnMemoria {
    private val _actual = MutableStateFlow<SesionResultados?>(null)
    val actual: StateFlow<SesionResultados?> = _actual.asStateFlow()

    fun publicar(origen: OrigenEntrada, estudiantes: List<ResultadoEstudiante>) {
        _actual.value = SesionResultados(origen, estudiantes)
    }

    fun limpiar() {
        _actual.value = null
    }
}
