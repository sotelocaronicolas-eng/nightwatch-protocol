package com.example.nightwatchprotocol.cybermind

/** El archivo de ejemplo que se descarga: el encabezado y un estudiante sintético con sus cinco temas (puntaje_global = promedio). */
object PlantillaCsv {
    const val NOMBRE_ARCHIVO = "plantilla_cybermind.csv"
    const val TIPO_MIME = "text/csv"

    // (aciertos básico, intermedio, avanzado, tiempo) por tema, en el orden de [Tema.entries].
    private val ejemplo = listOf(
        listOf(3, 2, 1, "32.5"),
        listOf(2, 2, 1, "41.2"),
        listOf(3, 3, 1, "28.9"),
        listOf(2, 1, 1, "55.0"),
        listOf(3, 3, 2, "24.8"),
    )

    fun contenido(): String {
        val puntajes = ejemplo.map { ReglasCyberMind.calcularPuntajeTema(it[0] as Int, it[1] as Int, it[2] as Int) }
        val global = Math.round(puntajes.average() * 10.0) / 10.0
        val filas = Tema.entries.mapIndexed { i, tema ->
            val e = ejemplo[i]
            listOf("E00001", tema.codigo, Experiencia.CURSOS.codigo, e[0], e[1], e[2], puntajes[i], global, e[3]).joinToString(",")
        }
        return (listOf(ValidadorCsv.COLUMNAS.joinToString(",")) + filas).joinToString("\n", postfix = "\n")
    }
}
