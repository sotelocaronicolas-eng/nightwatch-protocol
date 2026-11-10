package com.example.nightwatchprotocol.ui.cybermind

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.ErrorCsv
import com.example.nightwatchprotocol.cybermind.ReglasCyberMind
import com.example.nightwatchprotocol.cybermind.ValidadorCsv
import com.example.nightwatchprotocol.data.common.UiText

/** El texto de un error del CSV. Los nombres de columna y los ids de estudiante van tal cual (son datos, no se traducen). */
fun ErrorCsv.aTexto(): UiText = when (this) {
    is ErrorCsv.ArchivoDemasiadoGrande -> UiText.of(R.string.cybermind_csv_err_too_big, ValidadorCsv.MAX_BYTES / 1024)
    ErrorCsv.ArchivoVacio -> UiText.of(R.string.cybermind_csv_err_empty)
    ErrorCsv.NoEsUtf8 -> UiText.of(R.string.cybermind_csv_err_not_utf8)
    ErrorCsv.EncabezadoInvalido -> UiText.of(R.string.cybermind_csv_err_header)
    is ErrorCsv.ColumnaNoPermitida -> UiText.of(R.string.cybermind_csv_err_column_not_allowed, UiText.dynamic(nombre))
    is ErrorCsv.DemasiadasFilas -> UiText.of(R.string.cybermind_csv_err_too_many_rows, maximo)
    is ErrorCsv.DemasiadosEstudiantes -> UiText.of(R.string.cybermind_csv_err_too_many_students, maximo)
    is ErrorCsv.ColumnasIncorrectas -> UiText.of(R.string.cybermind_csv_err_row_columns, linea, ValidadorCsv.COLUMNAS.size, encontradas)
    is ErrorCsv.ValorVacio -> UiText.of(R.string.cybermind_csv_err_empty_value, linea, UiText.dynamic(columna))
    is ErrorCsv.ValorNoPermitido -> UiText.of(R.string.cybermind_csv_err_not_allowed, linea, UiText.dynamic(columna))
    is ErrorCsv.NoNumerico -> UiText.of(R.string.cybermind_csv_err_not_number, linea, UiText.dynamic(columna))
    is ErrorCsv.FueraDeRango -> UiText.of(R.string.cybermind_csv_err_out_of_range, linea, UiText.dynamic(columna), rangoDe(columna))
    is ErrorCsv.PuntajeTemaIncoherente -> UiText.of(R.string.cybermind_csv_err_score_mismatch, linea)
    is ErrorCsv.TemaRepetido -> UiText.of(R.string.cybermind_csv_err_topic_repeated, linea, UiText.dynamic(estudiante), UiText.dynamic(tema))
    is ErrorCsv.DemasiadosTemasPorEstudiante -> UiText.of(R.string.cybermind_csv_err_too_many_topics, UiText.dynamic(estudiante), maximo)
    is ErrorCsv.ExperienciaDistinta -> UiText.of(R.string.cybermind_csv_err_experience_differs, UiText.dynamic(estudiante))
    is ErrorCsv.PuntajeGlobalDistinto -> UiText.of(R.string.cybermind_csv_err_global_differs, UiText.dynamic(estudiante))
}

private fun rangoDe(columna: String): UiText = when (columna) {
    ValidadorCsv.COL_TIEMPO ->
        UiText.of(R.string.cybermind_csv_range, ReglasCyberMind.MIN_TIEMPO_SEG.toInt(), ReglasCyberMind.MAX_TIEMPO_SEG.toInt())
    ValidadorCsv.COL_PUNTAJE_TEMA, ValidadorCsv.COL_PUNTAJE_GLOBAL ->
        UiText.of(R.string.cybermind_csv_range, 0, ReglasCyberMind.MAX_PUNTAJE.toInt())
    else -> UiText.of(R.string.cybermind_csv_range, 0, ReglasCyberMind.MAX_ACIERTOS)
}

/** El título del panel de errores: con más de [ValidadorCsv.ERRORES_VISIBLES] dice cuántos se muestran de cuántos. */
fun tituloErrores(total: Int): UiText =
    if (total > ValidadorCsv.ERRORES_VISIBLES) {
        UiText.of(R.string.cybermind_csv_invalid_partial, ValidadorCsv.ERRORES_VISIBLES, total)
    } else {
        UiText.plural(R.plurals.cybermind_csv_invalid_all, total, total)
    }

/** "… y N errores más", o `null` si caben todos. */
fun restoDeErrores(total: Int): UiText? =
    (total - ValidadorCsv.ERRORES_VISIBLES).takeIf { it > 0 }?.let { UiText.plural(R.plurals.cybermind_csv_more_errors, it, it) }
