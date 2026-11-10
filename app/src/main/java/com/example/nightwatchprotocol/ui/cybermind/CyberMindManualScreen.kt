package com.example.nightwatchprotocol.ui.cybermind

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.ErroresFilaManual
import com.example.nightwatchprotocol.cybermind.Experiencia
import com.example.nightwatchprotocol.cybermind.FilaManual
import com.example.nightwatchprotocol.cybermind.ReglasCyberMind
import com.example.nightwatchprotocol.cybermind.Tema
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchTextError
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme
import com.example.nightwatchprotocol.ui.theme.errorMark

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

/** "Ingreso manual" (Figma 2677:614): la cantidad de temas, una fila abierta a la vez y "Evaluar" abajo. */
@Composable
fun CyberMindManualScreen(
    onClose: () -> Unit,
    onVerResultados: () -> Unit,
    viewModel: CyberMindManualViewModel = viewModel(factory = CyberMindManualViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CyberMindManualEvent.VerResultados -> onVerResultados()
            }
        }
    }

    CyberMindManualContent(
        uiState = uiState,
        onClose = onClose,
        onCantidadChange = viewModel::onCantidadChange,
        onExpandir = viewModel::onExpandir,
        onFilaChange = viewModel::onFilaChange,
        onEvaluar = viewModel::onEvaluar,
    )
}

@Composable
internal fun CyberMindManualContent(
    uiState: CyberMindManualUiState,
    onClose: () -> Unit,
    onCantidadChange: (Int) -> Unit,
    onExpandir: (Int) -> Unit,
    onFilaChange: (Int, (FilaManual) -> FilaManual) -> Unit,
    onEvaluar: () -> Unit,
) {
    val validacion = uiState.validacion
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = stringResource(R.string.cybermind_manual_title), onClose = onClose)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.cybermind_manual_intro),
                    color = NightWatchTheme.colors.textSecondary,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 13.sp,
                )
                SelectorCantidad(cantidad = uiState.cantidad, onChange = onCantidadChange)

                uiState.filas.forEachIndexed { indice, fila ->
                    if (indice == uiState.expandida) {
                        FilaAbierta(
                            indice = indice,
                            total = uiState.cantidad,
                            fila = fila,
                            errores = validacion.filas.getOrNull(indice) ?: ErroresFilaManual(),
                            otrosTemas = uiState.filas.filterIndexed { i, _ -> i != indice }.mapNotNull { it.tema }.toSet(),
                            onChange = { cambio -> onFilaChange(indice, cambio) },
                        )
                    } else {
                        FilaPlegada(indice = indice, tema = fila.tema, completa = !(validacion.filas.getOrNull(indice)?.hayError ?: true), onClick = { onExpandir(indice) })
                    }
                }

                if (validacion.experienciaDistinta) AvisoError(stringResource(R.string.cybermind_error_experience_differs))
                if (validacion.puntajeGlobalDistinto) AvisoError(stringResource(R.string.cybermind_error_global_differs))
                uiState.error?.let { AvisoError(it.asString()) }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                NightWatchButton(
                    text = stringResource(if (uiState.evaluando) R.string.cybermind_manual_evaluating else R.string.cybermind_manual_evaluate),
                    onClick = onEvaluar,
                    enabled = uiState.puedeEvaluar,
                )
            }
        }
    }
}

@Composable
private fun AvisoError(texto: String) {
    Text(
        text = errorMark(texto),
        color = NightWatchTextError,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 13.sp,
    )
}

@Composable
private fun SelectorCantidad(cantidad: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.cybermind_manual_count_label),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (ReglasCyberMind.MIN_TEMAS..ReglasCyberMind.MAX_TEMAS).forEach { n ->
                val elegido = n == cantidad
                val descripcion = stringResource(R.string.cybermind_manual_count_option, n)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            if (elegido) NightWatchTheme.colors.brandWhite else NightWatchTheme.colors.bgElevated,
                            FieldShape,
                        )
                        .border(BorderStroke(1.dp, NightWatchBorderAccent), FieldShape)
                        .semantics { selected = elegido; contentDescription = descripcion }
                        .clickable(role = Role.RadioButton) { onChange(n) },
                ) {
                    Text(
                        text = n.toString(),
                        color = if (elegido) NightWatchTheme.colors.textOnLight else NightWatchTheme.colors.onElevated,
                        fontFamily = NightWatchBodyFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaPlegada(indice: Int, tema: Tema?, completa: Boolean, onClick: () -> Unit) {
    val titulo = if (completa && tema != null) {
        stringResource(R.string.cybermind_manual_row_with_topic, indice + 1, stringResource(tema.nombreRes()))
    } else {
        stringResource(R.string.cybermind_manual_row_incomplete, indice + 1)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(NightWatchTheme.colors.bgElevated, CardShape)
            .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = titulo,
            color = NightWatchTheme.colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", color = NightWatchTheme.colors.onElevated, fontSize = 18.sp)
    }
}

@Composable
private fun FilaAbierta(
    indice: Int,
    total: Int,
    fila: FilaManual,
    errores: ErroresFilaManual,
    otrosTemas: Set<Tema>,
    onChange: ((FilaManual) -> FilaManual) -> Unit,
) {
    val locale = idiomaActual()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(NightWatchTheme.colors.bgElevated, CardShape)
            .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.cybermind_manual_row_title, indice + 1, total),
            color = NightWatchTheme.colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        SelectorOpciones(
            label = stringResource(R.string.cybermind_field_topic),
            valor = fila.tema,
            opciones = Tema.entries.filter { it !in otrosTemas },
            nombre = { stringResource(it.nombreRes()) },
            onSeleccion = { tema -> onChange { it.copy(tema = tema) } },
            error = errores.tema.texto(),
        )
        DosColumnas(
            izquierda = {
                SelectorOpciones(
                    label = stringResource(R.string.cybermind_field_experience),
                    valor = fila.experiencia,
                    opciones = Experiencia.entries,
                    nombre = { stringResource(it.nombreRes()) },
                    onSeleccion = { e -> onChange { it.copy(experiencia = e) } },
                    error = errores.experiencia.texto(),
                )
            },
            derecha = {
                SelectorAciertos(R.string.cybermind_field_basic, fila.aciertosBasico, errores.aciertosBasico.texto()) { v -> onChange { it.copy(aciertosBasico = v) } }
            },
        )
        DosColumnas(
            izquierda = {
                SelectorAciertos(R.string.cybermind_field_intermediate, fila.aciertosIntermedio, errores.aciertosIntermedio.texto()) { v -> onChange { it.copy(aciertosIntermedio = v) } }
            },
            derecha = {
                SelectorAciertos(R.string.cybermind_field_advanced, fila.aciertosAvanzado, errores.aciertosAvanzado.texto()) { v -> onChange { it.copy(aciertosAvanzado = v) } }
            },
        )
        DosColumnas(
            izquierda = { PuntajeCalculado(fila.puntajeTema?.let { formatoDecimal(it, locale) }) },
            derecha = {
                CampoDecimal(R.string.cybermind_field_global_score, fila.puntajeGlobal, errores.puntajeGlobal.texto()) { v -> onChange { it.copy(puntajeGlobal = v) } }
            },
        )
        CampoDecimal(R.string.cybermind_field_time, fila.tiempoPromedioSeg, errores.tiempoPromedioSeg.texto()) { v -> onChange { it.copy(tiempoPromedioSeg = v) } }
    }
}

@Composable
private fun DosColumnas(izquierda: @Composable () -> Unit, derecha: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) { izquierda() }
        Box(modifier = Modifier.weight(1f)) { derecha() }
    }
}

@Composable
private fun SelectorAciertos(labelRes: Int, valor: Int?, error: String?, onChange: (Int) -> Unit) {
    SelectorOpciones(
        label = stringResource(labelRes),
        valor = valor,
        opciones = (0..ReglasCyberMind.MAX_ACIERTOS).toList(),
        nombre = { it.toString() },
        onSeleccion = onChange,
        error = error,
    )
}

@Composable
private fun CampoDecimal(labelRes: Int, valor: String, error: String?, onChange: (String) -> Unit) {
    NightWatchTextField(
        label = stringResource(labelRes),
        value = valor,
        onValueChange = onChange,
        isError = error != null,
        supportingText = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** El puntaje del tema: de solo lectura, se calcula desde los tres aciertos. */
@Composable
private fun PuntajeCalculado(valor: String?) {
    Column {
        Text(
            text = stringResource(R.string.cybermind_field_topic_score),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
        )
        Surface(
            color = NightWatchTheme.colors.surfaceButtonMuted,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(56.dp),
        ) {
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = if (valor != null) stringResource(R.string.cybermind_field_topic_score_value, valor) else stringResource(R.string.cybermind_field_topic_score_pending),
                    color = NightWatchTheme.colors.onSurfaceButton,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
