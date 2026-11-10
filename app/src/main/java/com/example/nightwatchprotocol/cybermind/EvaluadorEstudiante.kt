package com.example.nightwatchprotocol.cybermind

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Versión del modelo que muestra la pantalla de detalle. */
const val MODEL_VERSION = "cybermind_gbdt-v1"

/** Decide AVANCE o REFUERZO para un tema. Bloqueante: quien llama lo corre fuera del hilo principal (ver [evaluarEstudiantes]). */
interface EvaluadorEstudiante {
    /** La versión guardada dentro del .onnx, si el modelo cargó y la trae; `null` con la regla de respaldo. */
    val versionEmbebida: String?

    fun evaluarTema(fila: FilaEstudiante): ResultadoTema
}

/** Evalúa todas las filas fuera del hilo principal y las agrupa por estudiante, en el orden en que aparecen. Cancelable. */
suspend fun EvaluadorEstudiante.evaluarEstudiantes(
    filas: List<FilaEstudiante>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<ResultadoEstudiante> = withContext(dispatcher) {
    val porEstudiante = LinkedHashMap<String, MutableList<ResultadoTema>>()
    for (fila in filas) {
        ensureActive()
        porEstudiante.getOrPut(fila.idEstudiante) { mutableListOf() } += evaluarTema(fila)
    }
    porEstudiante.map { (id, temas) -> ResultadoEstudiante(id, temas) }
}

/**
 * Envuelve [CyberMindModelo]. El modelo se pide con [modelo] y se crea una sola vez (la sesión de ONNX cuesta); si no carga,
 * `CyberMindModelo` ya aplica la regla del 70 % y aquí se marca como [OrigenEvaluacion.REGLA_RESPALDO].
 *
 * Crear el modelo puede fallar con un `Error` y no solo con una `Exception` (p. ej. `UnsatisfiedLinkError` si falta la librería
 * nativa de ONNX en esa arquitectura): se captura [Throwable], se recuerda el fallo y todo se evalúa con la regla del 70 %. La app
 * no debe cerrarse por esto.
 */
class OnnxEvaluadorEstudiante(modelo: () -> CyberMindModelo) : EvaluadorEstudiante {
    private val modelo: CyberMindModelo? by lazy {
        try {
            modelo()
        } catch (_: Throwable) {
            null
        }
    }

    override val versionEmbebida: String? get() = modelo?.version

    override fun evaluarTema(fila: FilaEstudiante): ResultadoTema {
        val modelo = modelo ?: return reglaDeRespaldo(fila)
        val decision = modelo.decidir(
            Diagnostico(
                tema = fila.tema.codigo,
                experienciaPrevia = fila.experiencia.codigo,
                aciertosBasico = fila.aciertosBasico,
                aciertosIntermedio = fila.aciertosIntermedio,
                aciertosAvanzado = fila.aciertosAvanzado,
                puntajeTema = fila.puntajeTema.toFloat(),
                puntajeGlobal = fila.puntajeGlobal.toFloat(),
                tiempoPromedioSeg = fila.tiempoPromedioSeg.toFloat(),
            ),
        )
        val conModelo = decision.origen == ORIGEN_MODELO
        val refuerzo = decision.ruta == Ruta.REFUERZO
        return ResultadoTema(
            tema = fila.tema,
            decision = if (refuerzo) Veredicto.REFUERZO else Veredicto.AVANCE,
            puntajeTema = fila.puntajeTema,
            confianza = if (!conModelo) null else if (refuerzo) decision.probabilidadRefuerzo else 1f - decision.probabilidadRefuerzo,
            origen = if (conModelo) OrigenEvaluacion.MODELO else OrigenEvaluacion.REGLA_RESPALDO,
        )
    }

    /** La regla del 70 % de `CyberMindModelo`, para cuando ni siquiera se pudo crear el modelo. */
    private fun reglaDeRespaldo(fila: FilaEstudiante) = ResultadoTema(
        tema = fila.tema,
        decision = if (fila.puntajeTema < UMBRAL_RESPALDO) Veredicto.REFUERZO else Veredicto.AVANCE,
        puntajeTema = fila.puntajeTema,
        confianza = null,
        origen = OrigenEvaluacion.REGLA_RESPALDO,
    )

    private companion object {
        const val ORIGEN_MODELO = "modelo_gbdt"
        const val UMBRAL_RESPALDO = 70.0
    }
}
