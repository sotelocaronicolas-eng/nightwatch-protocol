package com.example.nightwatchprotocol.ui.cybermind

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.ErrorCampo
import com.example.nightwatchprotocol.cybermind.Experiencia
import com.example.nightwatchprotocol.cybermind.Tema
import com.example.nightwatchprotocol.cybermind.Veredicto
import com.example.nightwatchprotocol.ui.components.NightWatchSelectField
import java.util.Locale

@StringRes
fun Tema.nombreRes(): Int = when (this) {
    Tema.FUNDAMENTOS_REDES -> R.string.cybermind_topic_fundamentos_redes
    Tema.CRIPTOGRAFIA -> R.string.cybermind_topic_criptografia
    Tema.SEGURIDAD_WEB -> R.string.cybermind_topic_seguridad_web
    Tema.INGENIERIA_SOCIAL -> R.string.cybermind_topic_ingenieria_social
    Tema.GESTION_VULNERABILIDADES -> R.string.cybermind_topic_gestion_vulnerabilidades
}

@StringRes
fun Experiencia.nombreRes(): Int = when (this) {
    Experiencia.NINGUNA -> R.string.cybermind_experience_ninguna
    Experiencia.CURSOS -> R.string.cybermind_experience_cursos
    Experiencia.LABORAL -> R.string.cybermind_experience_laboral
}

@StringRes
fun Veredicto.nombreRes(): Int = when (this) {
    Veredicto.AVANCE -> R.string.cybermind_verdict_avance
    Veredicto.REFUERZO -> R.string.cybermind_verdict_refuerzo
}

/** El idioma de la app en este momento (AppCompat lo aplica a la actividad, no al proceso). */
@Composable
fun idiomaActual(): Locale = LocalConfiguration.current.locales[0]

/** Un decimal con un solo decimal, con la coma o el punto del idioma (55,6 en español, 55.6 en inglés). */
fun formatoDecimal(valor: Double, locale: Locale): String = String.format(locale, "%.1f", valor)

/** El texto de un error de campo; `null` para "vacío" (un campo sin llenar no se marca como error mientras se escribe). */
@Composable
fun ErrorCampo?.texto(): String? = when (this) {
    null, ErrorCampo.Vacio -> null
    ErrorCampo.NoNumerico -> stringResource(R.string.cybermind_error_number)
    ErrorCampo.TemaRepetido -> stringResource(R.string.cybermind_error_topic_repeated)
    is ErrorCampo.FueraDeRango -> {
        val locale = idiomaActual()
        stringResource(R.string.cybermind_error_range, formatoRango(minimo, locale), formatoRango(maximo, locale))
    }
}

private fun formatoRango(valor: Double, locale: Locale): String =
    if (valor % 1.0 == 0.0) valor.toInt().toString() else formatoDecimal(valor, locale)

/** Un campo de elegir una opción de una lista (el campo del sistema de la app con su menú). */
@Composable
fun <T> SelectorOpciones(
    label: String,
    valor: T?,
    opciones: List<T>,
    nombre: @Composable (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        NightWatchSelectField(
            label = label,
            valueText = valor?.let { nombre(it) }.orEmpty(),
            onClick = { abierto = true },
            isError = error != null,
            supportingText = error,
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(nombre(opcion)) },
                    onClick = {
                        abierto = false
                        onSeleccion(opcion)
                    },
                )
            }
        }
    }
}
