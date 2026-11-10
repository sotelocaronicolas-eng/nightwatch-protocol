package com.example.nightwatchprotocol.cybermind

import kotlin.math.abs

/** Un motivo de rechazo del CSV. Lleva datos, no texto: la interfaz lo traduce. [linea] cuenta desde 1 (el encabezado es la 1). */
sealed interface ErrorCsv {
    data class ArchivoDemasiadoGrande(val bytes: Long) : ErrorCsv
    data object ArchivoVacio : ErrorCsv
    data object EncabezadoInvalido : ErrorCsv
    data class ColumnaNoPermitida(val nombre: String) : ErrorCsv
    data class DemasiadasFilas(val maximo: Int) : ErrorCsv
    data class DemasiadosEstudiantes(val maximo: Int) : ErrorCsv
    data class ColumnasIncorrectas(val linea: Int, val encontradas: Int) : ErrorCsv
    data class ValorVacio(val linea: Int, val columna: String) : ErrorCsv
    data class ValorNoPermitido(val linea: Int, val columna: String, val valor: String) : ErrorCsv
    data class NoNumerico(val linea: Int, val columna: String, val valor: String) : ErrorCsv
    data class FueraDeRango(val linea: Int, val columna: String, val valor: String) : ErrorCsv
    data class PuntajeTemaIncoherente(val linea: Int, val esperado: Double, val valor: Double) : ErrorCsv
    data class TemaRepetido(val linea: Int, val estudiante: String, val tema: String) : ErrorCsv
    data class DemasiadosTemasPorEstudiante(val estudiante: String, val maximo: Int) : ErrorCsv
    data class ExperienciaDistinta(val estudiante: String) : ErrorCsv
    data class PuntajeGlobalDistinto(val estudiante: String) : ErrorCsv
}

sealed interface ResultadoValidacionCsv {
    /** [filas] en el orden del archivo. */
    data class Valido(val filas: List<FilaEstudiante>) : ResultadoValidacionCsv

    /** Se rechaza el archivo completo; [errores] trae todos (la pantalla muestra los primeros 5 y "… y N errores más"). */
    data class Invalido(val errores: List<ErrorCsv>) : ResultadoValidacionCsv
}

/** Validación del CSV de estudiantes. Kotlin puro: sin Android, para probarla en la JVM. */
object ValidadorCsv {
    const val MAX_BYTES = 200L * 1024L
    const val MAX_FILAS = 500
    const val MAX_ESTUDIANTES = 100
    const val MAX_TEMAS_POR_ESTUDIANTE = ReglasCyberMind.MAX_TEMAS
    const val ERRORES_VISIBLES = 5

    const val COL_ID = "id_estudiante"
    const val COL_TEMA = "tema"
    const val COL_EXPERIENCIA = "experiencia_previa"
    const val COL_BASICO = "aciertos_basico"
    const val COL_INTERMEDIO = "aciertos_intermedio"
    const val COL_AVANZADO = "aciertos_avanzado"
    const val COL_PUNTAJE_TEMA = "puntaje_tema"
    const val COL_PUNTAJE_GLOBAL = "puntaje_global"
    const val COL_TIEMPO = "tiempo_promedio_seg"

    val COLUMNAS: List<String> = listOf(
        COL_ID, COL_TEMA, COL_EXPERIENCIA, COL_BASICO, COL_INTERMEDIO, COL_AVANZADO, COL_PUNTAJE_TEMA, COL_PUNTAJE_GLOBAL, COL_TIEMPO,
    )

    private val BOM = Char(0xFEFF)

    private val entero =Regex("^[0-9]+$")
    private val decimal = Regex("^[0-9]+(\\.[0-9]+)?$")

    /** Se llama ANTES de leer el archivo, con el tamaño que informa el sistema. */
    fun validarTamano(bytes: Long): ErrorCsv? = if (bytes > MAX_BYTES) ErrorCsv.ArchivoDemasiadoGrande(bytes) else null

    fun validar(texto: String): ResultadoValidacionCsv {
        val bytes = texto.toByteArray(Charsets.UTF_8).size.toLong()
        validarTamano(bytes)?.let { return ResultadoValidacionCsv.Invalido(listOf(it)) }

        val lineas = texto.removePrefix(BOM.toString()).split('\n').map { it.removeSuffix("\r") }.dropLastWhile { it.isEmpty() }
        if (lineas.isEmpty()) return ResultadoValidacionCsv.Invalido(listOf(ErrorCsv.ArchivoVacio))

        val encabezado = lineas.first().split(',').map { it.trim() }
        val noPermitidas = encabezado.filter { it !in COLUMNAS }
        if (noPermitidas.isNotEmpty()) {
            return ResultadoValidacionCsv.Invalido(noPermitidas.map { ErrorCsv.ColumnaNoPermitida(it) })
        }
        if (encabezado != COLUMNAS) return ResultadoValidacionCsv.Invalido(listOf(ErrorCsv.EncabezadoInvalido))

        val datos = lineas.drop(1)
        if (datos.size > MAX_FILAS) return ResultadoValidacionCsv.Invalido(listOf(ErrorCsv.DemasiadasFilas(MAX_FILAS)))

        val errores = mutableListOf<ErrorCsv>()
        val filas = mutableListOf<FilaEstudiante>()
        datos.forEachIndexed { indice, linea -> leerFila(linea, indice + 2, errores)?.let { filas += it } }

        validarPorEstudiante(filas, datos, errores)

        return if (errores.isEmpty()) ResultadoValidacionCsv.Valido(filas) else ResultadoValidacionCsv.Invalido(errores)
    }

    private fun leerFila(linea: String, numero: Int, errores: MutableList<ErrorCsv>): FilaEstudiante? {
        val celdas = linea.split(',').map { it.trim() }
        if (celdas.size != COLUMNAS.size) {
            errores += ErrorCsv.ColumnasIncorrectas(numero, celdas.size)
            return null
        }
        val antes = errores.size

        COLUMNAS.forEachIndexed { i, columna ->
            if (celdas[i].isEmpty()) errores += ErrorCsv.ValorVacio(numero, columna)
        }
        if (errores.size > antes) return null

        val tema = Tema.desdeCodigo(celdas[1])
        if (tema == null) errores += ErrorCsv.ValorNoPermitido(numero, COL_TEMA, celdas[1])
        val experiencia = Experiencia.desdeCodigo(celdas[2])
        if (experiencia == null) errores += ErrorCsv.ValorNoPermitido(numero, COL_EXPERIENCIA, celdas[2])

        val basico = acierto(celdas[3], numero, COL_BASICO, errores)
        val intermedio = acierto(celdas[4], numero, COL_INTERMEDIO, errores)
        val avanzado = acierto(celdas[5], numero, COL_AVANZADO, errores)
        val puntajeTema = decimalEnRango(celdas[6], numero, COL_PUNTAJE_TEMA, 0.0, ReglasCyberMind.MAX_PUNTAJE, errores)
        val puntajeGlobal = decimalEnRango(celdas[7], numero, COL_PUNTAJE_GLOBAL, 0.0, ReglasCyberMind.MAX_PUNTAJE, errores)
        val tiempo = decimalEnRango(
            celdas[8], numero, COL_TIEMPO, ReglasCyberMind.MIN_TIEMPO_SEG, ReglasCyberMind.MAX_TIEMPO_SEG, errores,
        )

        if (basico != null && intermedio != null && avanzado != null && puntajeTema != null) {
            val esperado = ReglasCyberMind.calcularPuntajeTema(basico, intermedio, avanzado)
            if (abs(esperado - puntajeTema) > ReglasCyberMind.TOLERANCIA_PUNTAJE + 1e-9) {
                errores += ErrorCsv.PuntajeTemaIncoherente(numero, esperado, puntajeTema)
            }
        }

        if (errores.size > antes || tema == null || experiencia == null || basico == null || intermedio == null ||
            avanzado == null || puntajeTema == null || puntajeGlobal == null || tiempo == null
        ) {
            return null
        }
        return FilaEstudiante(celdas[0], tema, experiencia, basico, intermedio, avanzado, puntajeTema, puntajeGlobal, tiempo)
    }

    private fun acierto(valor: String, linea: Int, columna: String, errores: MutableList<ErrorCsv>): Int? {
        if (!entero.matches(valor)) {
            errores += ErrorCsv.NoNumerico(linea, columna, valor)
            return null
        }
        val n = valor.toIntOrNull()
        if (n == null || n > ReglasCyberMind.MAX_ACIERTOS) {
            errores += ErrorCsv.FueraDeRango(linea, columna, valor)
            return null
        }
        return n
    }

    private fun decimalEnRango(
        valor: String,
        linea: Int,
        columna: String,
        minimo: Double,
        maximo: Double,
        errores: MutableList<ErrorCsv>,
    ): Double? {
        if (!decimal.matches(valor)) {
            errores += ErrorCsv.NoNumerico(linea, columna, valor)
            return null
        }
        val n = valor.toDouble()
        if (n < minimo || n > maximo) {
            errores += ErrorCsv.FueraDeRango(linea, columna, valor)
            return null
        }
        return n
    }

    /**
     * Reglas que miran a un estudiante entero. Solo cuentan las filas ya válidas; un tema repetido se señala en la línea de la
     * segunda aparición (se recorre en el orden del archivo, por eso se conserva el número de línea de cada fila).
     */
    private fun validarPorEstudiante(filas: List<FilaEstudiante>, datos: List<String>, errores: MutableList<ErrorCsv>) {
        // Para el número de línea: la fila válida n-ésima no coincide con la línea n-ésima si hubo filas con error, así que se
        // relee el id y el tema de cada línea de datos.
        val porEstudiante = LinkedHashMap<String, MutableList<Pair<Int, Tema?>>>()
        datos.forEachIndexed { i, linea ->
            val celdas = linea.split(',').map { it.trim() }
            if (celdas.size == COLUMNAS.size && celdas[0].isNotEmpty()) {
                porEstudiante.getOrPut(celdas[0]) { mutableListOf() } += (i + 2) to Tema.desdeCodigo(celdas[1])
            }
        }

        if (porEstudiante.size > MAX_ESTUDIANTES) errores += ErrorCsv.DemasiadosEstudiantes(MAX_ESTUDIANTES)

        porEstudiante.forEach { (id, apariciones) ->
            val vistos = mutableSetOf<Tema>()
            apariciones.forEach { (linea, tema) ->
                if (tema != null && !vistos.add(tema)) errores += ErrorCsv.TemaRepetido(linea, id, tema.codigo)
            }
            if (apariciones.size > MAX_TEMAS_POR_ESTUDIANTE) {
                errores += ErrorCsv.DemasiadosTemasPorEstudiante(id, MAX_TEMAS_POR_ESTUDIANTE)
            }
        }

        filas.groupBy { it.idEstudiante }.forEach { (id, delEstudiante) ->
            if (delEstudiante.map { it.experiencia }.distinct().size > 1) errores += ErrorCsv.ExperienciaDistinta(id)
            if (delEstudiante.map { it.puntajeGlobal }.distinct().size > 1) errores += ErrorCsv.PuntajeGlobalDistinto(id)
        }
    }
}
