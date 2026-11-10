package com.example.nightwatchprotocol.cybermind

/** Lo que la persona va llenando en una fila del ingreso manual. Los números decimales viajan como texto, tal como se escriben. */
data class FilaManual(
    val tema: Tema? = null,
    val experiencia: Experiencia? = null,
    val aciertosBasico: Int? = null,
    val aciertosIntermedio: Int? = null,
    val aciertosAvanzado: Int? = null,
    val puntajeGlobal: String = "",
    val tiempoPromedioSeg: String = "",
) {
    /** Calculado solo desde los tres aciertos; `null` mientras falte alguno. */
    val puntajeTema: Double?
        get() = if (aciertosBasico != null && aciertosIntermedio != null && aciertosAvanzado != null) {
            ReglasCyberMind.calcularPuntajeTema(aciertosBasico, aciertosIntermedio, aciertosAvanzado)
        } else {
            null
        }
}

sealed interface ErrorCampo {
    data object Vacio : ErrorCampo
    data object NoNumerico : ErrorCampo
    data class FueraDeRango(val minimo: Double, val maximo: Double) : ErrorCampo
    data object TemaRepetido : ErrorCampo
}

data class ErroresFilaManual(
    val tema: ErrorCampo? = null,
    val experiencia: ErrorCampo? = null,
    val aciertosBasico: ErrorCampo? = null,
    val aciertosIntermedio: ErrorCampo? = null,
    val aciertosAvanzado: ErrorCampo? = null,
    val puntajeGlobal: ErrorCampo? = null,
    val tiempoPromedioSeg: ErrorCampo? = null,
) {
    val hayError: Boolean
        get() = listOf(tema, experiencia, aciertosBasico, aciertosIntermedio, aciertosAvanzado, puntajeGlobal, tiempoPromedioSeg)
            .any { it != null }
}

data class ValidacionManual(
    val filas: List<ErroresFilaManual>,
    val cantidadInvalida: Boolean,
    val experienciaDistinta: Boolean,
    val puntajeGlobalDistinto: Boolean,
    /** Las filas listas para evaluar; `null` si algo no es válido. */
    val entrada: List<FilaEstudiante>?,
) {
    val esValido: Boolean get() = entrada != null
}

/** Validación del ingreso manual. Kotlin puro: sin Android. El ID del estudiante lo pone quien llama (se asigna solo). */
object ValidadorManual {

    fun validar(idEstudiante: String, filas: List<FilaManual>): ValidacionManual {
        val cantidadInvalida = filas.size !in ReglasCyberMind.MIN_TEMAS..ReglasCyberMind.MAX_TEMAS
        val globales = filas.map { parseDecimal(it.puntajeGlobal, 0.0, ReglasCyberMind.MAX_PUNTAJE) }
        val tiempos = filas.map { parseDecimal(it.tiempoPromedioSeg, ReglasCyberMind.MIN_TIEMPO_SEG, ReglasCyberMind.MAX_TIEMPO_SEG) }

        val errores = filas.mapIndexed { i, fila ->
            val repetido = fila.tema != null && filas.take(i).any { it.tema == fila.tema }
            ErroresFilaManual(
                tema = when {
                    fila.tema == null -> ErrorCampo.Vacio
                    repetido -> ErrorCampo.TemaRepetido
                    else -> null
                },
                experiencia = if (fila.experiencia == null) ErrorCampo.Vacio else null,
                aciertosBasico = errorAcierto(fila.aciertosBasico),
                aciertosIntermedio = errorAcierto(fila.aciertosIntermedio),
                aciertosAvanzado = errorAcierto(fila.aciertosAvanzado),
                puntajeGlobal = globales[i].error,
                tiempoPromedioSeg = tiempos[i].error,
            )
        }

        val experienciaDistinta = filas.mapNotNull { it.experiencia }.distinct().size > 1
        val puntajeGlobalDistinto = globales.mapNotNull { it.valor }.distinct().size > 1

        val valido = !cantidadInvalida && errores.none { it.hayError } && !experienciaDistinta && !puntajeGlobalDistinto
        val entrada = if (valido) {
            filas.mapIndexed { i, f ->
                FilaEstudiante(
                    idEstudiante = idEstudiante,
                    tema = f.tema!!,
                    experiencia = f.experiencia!!,
                    aciertosBasico = f.aciertosBasico!!,
                    aciertosIntermedio = f.aciertosIntermedio!!,
                    aciertosAvanzado = f.aciertosAvanzado!!,
                    puntajeTema = f.puntajeTema!!,
                    puntajeGlobal = globales[i].valor!!,
                    tiempoPromedioSeg = tiempos[i].valor!!,
                )
            }
        } else {
            null
        }
        return ValidacionManual(errores, cantidadInvalida, experienciaDistinta, puntajeGlobalDistinto, entrada)
    }

    private fun errorAcierto(valor: Int?): ErrorCampo? = when {
        valor == null -> ErrorCampo.Vacio
        valor !in 0..ReglasCyberMind.MAX_ACIERTOS -> ErrorCampo.FueraDeRango(0.0, ReglasCyberMind.MAX_ACIERTOS.toDouble())
        else -> null
    }

    private class Decimal(val valor: Double?, val error: ErrorCampo?)

    /** Acepta coma o punto al escribir (el teclado en español suele ofrecer la coma); los demás formatos no. */
    private fun parseDecimal(texto: String, minimo: Double, maximo: Double): Decimal {
        val limpio = texto.trim().replace(',', '.')
        if (limpio.isEmpty()) return Decimal(null, ErrorCampo.Vacio)
        if (!Regex("^[0-9]+(\\.[0-9]+)?$").matches(limpio)) return Decimal(null, ErrorCampo.NoNumerico)
        val n = limpio.toDouble()
        if (n < minimo || n > maximo) return Decimal(null, ErrorCampo.FueraDeRango(minimo, maximo))
        return Decimal(n, null)
    }
}
