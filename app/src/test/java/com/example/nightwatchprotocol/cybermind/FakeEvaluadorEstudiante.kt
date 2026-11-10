package com.example.nightwatchprotocol.cybermind

/**
 * Evaluador falso para las pruebas: aplica la regla del 70 % con [origen] y [confianza] fijos, y guarda lo que recibió.
 * [falla] hace que evaluar lance una excepción (para probar el camino de error).
 */
class FakeEvaluadorEstudiante(
    private val origen: OrigenEvaluacion = OrigenEvaluacion.MODELO,
    private val confianza: Float? = 0.9f,
    override val versionEmbebida: String? = null,
    var falla: Boolean = false,
) : EvaluadorEstudiante {

    val recibidas = mutableListOf<FilaEstudiante>()

    override fun evaluarTema(fila: FilaEstudiante): ResultadoTema {
        if (falla) throw IllegalStateException("fallo simulado")
        recibidas += fila
        return ResultadoTema(
            tema = fila.tema,
            decision = if (fila.puntajeTema < 70.0) Veredicto.REFUERZO else Veredicto.AVANCE,
            puntajeTema = fila.puntajeTema,
            confianza = confianza,
            origen = origen,
        )
    }
}
