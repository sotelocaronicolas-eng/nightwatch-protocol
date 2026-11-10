package com.example.nightwatchprotocol.ui.cybermind

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.cybermind.Experiencia
import com.example.nightwatchprotocol.cybermind.PlantillaCsv
import com.example.nightwatchprotocol.cybermind.Tema
import com.example.nightwatchprotocol.cybermind.ValidadorCsv
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSecondaryText
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme
import com.example.nightwatchprotocol.ui.theme.errorMark

private val CardShape = RoundedCornerShape(16.dp)

/** Lo que el selector de archivos ofrece como CSV: muchos teléfonos marcan un .csv como texto plano o como binario genérico. */
private val TiposCsv = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/csv",
    "application/vnd.ms-excel",
    "text/plain",
    "application/octet-stream",
)

/** "Evaluar desde un CSV" (Figma 2677:679 y 2677:709). */
@Composable
fun CyberMindCsvScreen(
    onClose: () -> Unit,
    onVerResultados: () -> Unit,
    viewModel: CyberMindCsvViewModel = viewModel(factory = CyberMindCsvViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onArchivoElegido(uri?.toString())
    }
    val guardarPlantilla = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PlantillaCsv.TIPO_MIME)) { uri ->
        viewModel.onPlantillaDestino(uri?.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CyberMindCsvEvent.VerResultados -> onVerResultados()
            }
        }
    }

    CyberMindCsvContent(
        uiState = uiState,
        onClose = onClose,
        onElegirArchivo = { abrirArchivo.launch(TiposCsv) },
        onDescargarPlantilla = { guardarPlantilla.launch(PlantillaCsv.NOMBRE_ARCHIVO) },
        onQuitarArchivo = viewModel::onQuitarArchivo,
        onEvaluar = viewModel::onEvaluar,
    )
}

@Composable
internal fun CyberMindCsvContent(
    uiState: CyberMindCsvUiState,
    onClose: () -> Unit,
    onElegirArchivo: () -> Unit,
    onDescargarPlantilla: () -> Unit,
    onQuitarArchivo: () -> Unit,
    onEvaluar: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = stringResource(R.string.cybermind_csv_title), onClose = onClose)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                TarjetaFormato()
                OutlinedButton(
                    onClick = onDescargarPlantilla,
                    shape = NightWatchPillShape,
                    border = BorderStroke(2.dp, NightWatchOnBackground),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.cybermind_csv_download_template), color = NightWatchOnBackground)
                }
                uiState.avisoPlantilla?.let {
                    Text(
                        text = stringResource(
                            if (it == AvisoPlantilla.GUARDADA) R.string.cybermind_csv_template_saved else R.string.cybermind_csv_template_failed,
                        ),
                        color = NightWatchSecondaryText,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                    )
                }
                ZonaElegir(onClick = onElegirArchivo, enabled = !uiState.ocupado)

                if (uiState.leyendo) Progreso(R.string.cybermind_csv_reading)
                if (uiState.evaluando) Progreso(R.string.cybermind_manual_evaluating)

                uiState.archivo?.let { archivo ->
                    Text(
                        text = stringResource(R.string.cybermind_csv_selected_label),
                        color = NightWatchOnBackground,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                    )
                    TarjetaArchivo(archivo = archivo, onQuitar = onQuitarArchivo)
                    if (uiState.errores.isNotEmpty()) PanelErrores(uiState.errores.take(ValidadorCsv.ERRORES_VISIBLES).map { it.aTexto().asString() }, uiState.errores.size)
                }
                uiState.error?.let { Text(errorMark(it.asString()), color = NightWatchTheme.colors.textError, fontSize = 13.sp) }
            }
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                NightWatchButton(
                    text = stringResource(R.string.cybermind_manual_evaluate),
                    onClick = onEvaluar,
                    enabled = uiState.puedeEvaluar,
                )
            }
        }
    }
}

@Composable
private fun TarjetaFormato() {
    val colors = NightWatchTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated, CardShape)
            .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.cybermind_csv_format_title),
            color = colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(stringResource(R.string.cybermind_csv_format_columns_intro), color = colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
        Text(ValidadorCsv.COLUMNAS.joinToString(", "), color = colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
        Text(
            text = stringResource(
                R.string.cybermind_csv_format_values,
                Tema.entries.joinToString(", ") { it.codigo },
                Experiencia.entries.joinToString(", ") { it.codigo },
            ),
            color = colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 12.sp,
        )
        Text(
            text = stringResource(
                R.string.cybermind_csv_format_limits,
                ValidadorCsv.MAX_FILAS,
                ValidadorCsv.MAX_ESTUDIANTES,
                (ValidadorCsv.MAX_BYTES / 1024).toInt(),
            ),
            color = colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ZonaElegir(onClick: () -> Unit, enabled: Boolean) {
    val borde = NightWatchOnBackground
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .drawBehind {
                drawRoundRect(
                    color = borde,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.cybermind_csv_pick),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Progreso(textoRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NightWatchOnBackground)
        Text(stringResource(textoRes), color = NightWatchOnBackground, fontFamily = NightWatchBodyFontFamily, fontSize = 13.sp)
    }
}

@Composable
private fun TarjetaArchivo(archivo: ArchivoCsvElegido, onQuitar: () -> Unit) {
    val colors = NightWatchTheme.colors
    val kb = ((archivo.bytes + 1023) / 1024).coerceAtLeast(1).toInt()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceButton, CardShape)
            .border(BorderStroke(1.dp, NightWatchBorderAccent), CardShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = archivo.nombre ?: stringResource(R.string.cybermind_csv_unnamed),
                color = colors.onSurfaceButton,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 14.sp,
            )
            Text(
                text = if (archivo.valido) {
                    stringResource(R.string.cybermind_csv_stats_valid, kb, archivo.filas, archivo.estudiantes)
                } else {
                    stringResource(R.string.cybermind_csv_stats_invalid, kb, archivo.filas)
                },
                color = colors.textMutedOnLight,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 12.sp,
            )
        }
        Insignia(
            texto = stringResource(if (archivo.valido) R.string.cybermind_csv_badge_valid else R.string.cybermind_csv_badge_invalid),
            fondo = if (archivo.valido) colors.success else colors.failAccent,
        )
        Spacer(Modifier.width(8.dp))
        val quitar = stringResource(R.string.cybermind_csv_remove)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onQuitar),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = quitar,
                tint = colors.onSurfaceButton,
            )
        }
    }
}

@Composable
private fun Insignia(texto: String, fondo: Color) {
    Text(
        text = texto,
        color = NightWatchTheme.colors.brandWhite,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.background(fondo, NightWatchPillShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Los primeros [ValidadorCsv.ERRORES_VISIBLES] errores y "… y N errores más". */
@Composable
private fun PanelErrores(textos: List<String>, total: Int) {
    val colors = NightWatchTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgElevated, CardShape)
            .border(BorderStroke(2.dp, NightWatchError), CardShape)
            .padding(18.dp),
    ) {
        Text(
            text = errorMark(tituloErrores(total).asString()),
            color = colors.onElevated,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        textos.take(ValidadorCsv.ERRORES_VISIBLES).forEach {
            Text(it, color = colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontSize = 13.sp)
        }
        restoDeErrores(total)?.let {
            Text(it.asString(), color = colors.onElevated, fontFamily = NightWatchBodyFontFamily, fontSize = 13.sp)
        }
    }
}
