package com.example.nightwatchprotocol.cybermind

import kotlin.math.roundToLong

/** Los cinco temas del modelo. [codigo] es el valor exacto que va al modelo y al CSV; el nombre amigable lo pone la interfaz. */
enum class Tema(val codigo: String) {
    FUNDAMENTOS_REDES("fundamentos_redes"),
    CRIPTOGRAFIA("criptografia"),
    SEGURIDAD_WEB("seguridad_web"),
    INGENIERIA_SOCIAL("ingenieria_social"),
    GESTION_VULNERABILIDADES("gestion_vulnerabilidades"),
    ;

    companion object {
        fun desdeCodigo(codigo: String): Tema? = entries.firstOrNull { it.codigo == codigo }
    }
}

enum class Experiencia(val codigo: String) {
    NINGUNA("ninguna"),
    CURSOS("cursos"),
    LABORAL("laboral"),
    ;

    companion object {
        fun desdeCodigo(codigo: String): Experiencia? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Una fila de entrada del modelo: un estudiante en un tema (las 9 columnas, en el orden del CSV). */
data class FilaEstudiante(
    val idEstudiante: String,
    val tema: Tema,
    val experiencia: Experiencia,
    val aciertosBasico: Int,
    val aciertosIntermedio: Int,
    val aciertosAvanzado: Int,
    val puntajeTema: Double,
    val puntajeGlobal: Double,
    val tiempoPromedioSeg: Double,
)

/** La decisión de la interfaz (no es la clase `Decision` de [CyberMindModelo], que trae la probabilidad y el origen). */
enum class Veredicto { AVANCE, REFUERZO }

/** Quién tomó la decisión: el modelo de IA o la regla de respaldo del 70 % (cuando el modelo no carga o falla). */
enum class OrigenEvaluacion { MODELO, REGLA_RESPALDO }

/**
 * La decisión de un tema. [confianza] es la probabilidad (0 a 1) de la decisión mostrada; con la regla de respaldo es `null`,
 * porque ahí no hay probabilidad real (la regla solo compara con el 70 %).
 */
data class ResultadoTema(
    val tema: Tema,
    val decision: Veredicto,
    val puntajeTema: Double,
    val confianza: Float?,
    val origen: OrigenEvaluacion,
)

data class ResultadoEstudiante(val idEstudiante: String, val temas: List<ResultadoTema>) {
    val avances: Int get() = temas.count { it.decision == Veredicto.AVANCE }
    val refuerzos: Int get() = temas.count { it.decision == Veredicto.REFUERZO }

    /** Respaldo si al menos un tema se evaluó con la regla; así la pantalla nunca afirma "modelo" de más. */
    val origen: OrigenEvaluacion
        get() = if (temas.any { it.origen == OrigenEvaluacion.REGLA_RESPALDO }) OrigenEvaluacion.REGLA_RESPALDO else OrigenEvaluacion.MODELO
}

object ReglasCyberMind {
    const val MAX_ACIERTOS = 3
    const val MIN_TEMAS = 1
    const val MAX_TEMAS = 5
    const val MIN_TIEMPO_SEG = 4.0
    const val MAX_TIEMPO_SEG = 180.0
    const val MAX_PUNTAJE = 100.0

    /** Tolerancia al comparar `puntaje_tema` con lo calculado desde los aciertos. */
    const val TOLERANCIA_PUNTAJE = 0.05

    /** Tolerancia al comparar `puntaje_global` con el promedio de los `puntaje_tema` de un estudiante con los 5 temas. */
    const val TOLERANCIA_PROMEDIO_GLOBAL = 0.1

    /** `round(suma de los 3 aciertos / 9 * 100, 1)`. */
    fun calcularPuntajeTema(basico: Int, intermedio: Int, avanzado: Int): Double =
        ((basico + intermedio + avanzado) / 9.0 * 1000.0).roundToLong() / 10.0
}
