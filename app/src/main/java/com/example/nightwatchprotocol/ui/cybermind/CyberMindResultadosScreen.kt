package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.OrigenEntrada
import com.example.nightwatchprotocol.cybermind.OrigenEvaluacion
import com.example.nightwatchprotocol.cybermind.ResultadoEstudiante
import com.example.nightwatchprotocol.cybermind.ResultadoTema
import com.example.nightwatchprotocol.cybermind.Veredicto
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSecondaryText
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme
import java.util.Locale
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(16.dp)

/** "Resultados" (Figma 2677:746): el resumen y un estudiante por fila; tocar una fila abre sus temas. */
@Composable
fun CyberMindResultadosScreen(
    onClose: () -> Unit,
    onOpenEstudiante: (String) -> Unit,
    onNuevaEvaluacion: (OrigenEntrada) -> Unit,
    viewModel: CyberMindResultadosViewModel = viewModel(factory = CyberMindResultadosViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CyberMindResultadosContent(uiState, onClose, onOpenEstudiante, onNuevaEvaluacion)
}

@Composable
internal fun CyberMindResultadosContent(
    uiState: CyberMindResultadosUiState,
    onClose: () -> Unit,
    onOpenEstudiante: (String) -> Unit,
    onNuevaEvaluacion: (OrigenEntrada) -> Unit,
) {
    val origen = uiState.origen ?: OrigenEntrada.CSV
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = stringResource(R.string.cybermind_results_title), onClose = onClose)
            if (uiState.sesion == null) {
                Text(
                    text = stringResource(R.string.cybermind_results_empty),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(20.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    item(key = "resumen") {
                        Resumen(uiState.estudiantes.size, uiState.avances, uiState.refuerzos)
                    }
                    item(key = "pista") {
                        Text(
                            text = stringResource(R.string.cybermind_results_hint),
                            color = NightWatchSecondaryText,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 12.sp,
                        )
                    }
                    items(uiState.estudiantes, key = { it.idEstudiante }) { estudiante ->
                        FilaEstudiante(estudiante) { onOpenEstudiante(estudiante.idEstudiante) }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                NightWatchButton(
                    text = stringResource(if (origen == OrigenEntrada.MANUAL) R.string.cybermind_results_another else R.string.cybermind_results_new),
                    onClick = { onNuevaEvaluacion(origen) },
                )
            }
        }
    }
}

@Composable
private fun Resumen(estudiantes: Int, avances: Int, refuerzos: Int) {
    val colors = NightWatchTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated, CardShape)
            .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
            .padding(vertical = 18.dp),
    ) {
        Dato(estudiantes, UiText.plural(R.plurals.cybermind_results_students, estudiantes).asString())
        Dato(avances, stringResource(Veredicto.AVANCE.nombreRes()))
        Dato(refuerzos, stringResource(Veredicto.REFUERZO.nombreRes()))
    }
}

@Composable
private fun Dato(valor: Int, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = valor.toString(),
            color = NightWatchTheme.colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
        Text(text = etiqueta, color = NightWatchTheme.colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
    }
}

@Composable
private fun FilaEstudiante(estudiante: ResultadoEstudiante, onClick: () -> Unit) {
    val colors = NightWatchTheme.colors
    val total = estudiante.temas.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceButton, CardShape)
            .border(BorderStroke(3.dp, NightWatchBorderAccent), CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(estudiante.idEstudiante, color = colors.onSurfaceButton, fontFamily = NightWatchBodyFontFamily, fontSize = 15.sp)
            Text(
                text = UiText.plural(R.plurals.cybermind_results_row_subtitle, total, estudiante.avances, total).asString(),
                color = colors.textMutedOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 12.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
            ChipVeredicto(Veredicto.AVANCE, estudiante.avances)
            ChipVeredicto(Veredicto.REFUERZO, estudiante.refuerzos)
        }
        Text(text = "›", color = colors.onSurfaceButton, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** El color de cada decisión: verde para AVANCE y el acento de "perdido" del tema para REFUERZO. */
@Composable
private fun colorDe(veredicto: Veredicto) =
    if (veredicto == Veredicto.AVANCE) NightWatchTheme.colors.success else NightWatchTheme.colors.failAccent

@Composable
private fun ChipVeredicto(veredicto: Veredicto, cantidad: Int) {
    Text(
        text = stringResource(R.string.cybermind_chip, stringResource(veredicto.nombreRes()), cantidad),
        color = NightWatchTheme.colors.brandWhite,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.background(colorDe(veredicto), NightWatchPillShape).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** "Detalle" de un estudiante (Figma 2677:836): una fila por tema, con la decisión, el puntaje y la confianza. */
@Composable
fun CyberMindDetalleScreen(
    onClose: () -> Unit,
    viewModel: CyberMindDetalleViewModel = viewModel(factory = CyberMindDetalleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val estudiante = uiState.estudiante
    // Sin los datos en memoria (la app se reinició con el detalle abierto) no hay nada que mostrar.
    LaunchedEffect(estudiante == null) { if (estudiante == null) onClose() }
    if (estudiante == null) return

    val colors = NightWatchTheme.colors
    val locale = idiomaActual()
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = estudiante.idEstudiante, onClose = onClose)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgElevated, CardShape)
                        .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
                        .padding(18.dp),
                ) {
                    val resumen = UiText.of(
                        R.string.cybermind_detail_summary,
                        UiText.plural(R.plurals.cybermind_detail_avance_count, estudiante.avances, estudiante.avances),
                        UiText.plural(R.plurals.cybermind_detail_refuerzo_count, estudiante.refuerzos, estudiante.refuerzos),
                    )
                    Text(resumen.asString(), color = colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = stringResource(
                            if (estudiante.origen == OrigenEvaluacion.MODELO) R.string.cybermind_detail_origin_model else R.string.cybermind_detail_origin_fallback,
                        ),
                        color = colors.onElevated,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 12.sp,
                    )
                }
                estudiante.temas.forEach { FilaTema(it, locale) }
                Text(
                    text = stringResource(R.string.cybermind_detail_note),
                    color = NightWatchSecondaryText,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 12.sp,
                )
                Text(
                    text = uiState.versionEmbebida?.let { stringResource(R.string.cybermind_detail_model_version_embedded, uiState.versionModelo, it) }
                        ?: stringResource(R.string.cybermind_detail_model_version, uiState.versionModelo),
                    color = NightWatchSecondaryText,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                NightWatchButton(text = stringResource(R.string.cybermind_detail_back), onClick = onClose)
            }
        }
    }
}

@Composable
private fun FilaTema(resultado: ResultadoTema, locale: Locale) {
    val colors = NightWatchTheme.colors
    val puntaje = formatoDecimal(resultado.puntajeTema, locale)
    val linea = resultado.confianza?.let {
        stringResource(R.string.cybermind_detail_row_score_confidence, puntaje, (it * 100).roundToInt().toString())
    } ?: stringResource(R.string.cybermind_detail_row_score, puntaje)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceButton, CardShape)
            .border(BorderStroke(3.dp, NightWatchBorderAccent), CardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(resultado.tema.nombreRes()), color = colors.onSurfaceButton, fontFamily = NightWatchBodyFontFamily, fontSize = 15.sp)
            Text(linea, color = colors.textMutedOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
        }
        Text(
            text = stringResource(resultado.decision.nombreRes()),
            color = colors.brandWhite,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.background(colorDe(resultado.decision), NightWatchPillShape).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
