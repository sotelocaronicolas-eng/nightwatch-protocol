package com.example.nightwatchprotocol.ui.social

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.REPORT_NOTE_MAX_LENGTH
import com.example.nightwatchprotocol.data.social.ReportReason
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/*
 * "Reportar" and the confirmation of "Bloquear" (script 0011). They are dialogs and not routes of their own on purpose: from the arena
 * a new destination covers the battle, and a covered arena stops asking the server for the state of the battle (it only polls while
 * it is visible); after 60 seconds without a call the server gives the battle as lost. A dialog keeps the battle on screen.
 *
 * A screen that offers them keeps ONE [ReportBlockState], shows [ReportBlockLinks] where it wants them and draws [ReportBlockDialogs].
 */

enum class ReportBlockKind { Report, Block }

/**
 * Which dialog is open and for whom. It is saved with the screen (it survives a rotation). [opens] changes each time a dialog is
 * opened, so the second time the form starts empty instead of showing the answer of the first.
 */
@Stable
class ReportBlockState {
    var kind: ReportBlockKind? by mutableStateOf(null)
        private set
    var username: String by mutableStateOf("")
        private set
    var opens: Int by mutableIntStateOf(0)
        private set

    fun openReport(username: String) = open(ReportBlockKind.Report, username)

    fun openBlock(username: String) = open(ReportBlockKind.Block, username)

    fun close() {
        kind = null
    }

    private fun open(kind: ReportBlockKind, username: String) {
        this.username = username
        this.kind = kind
        opens++
    }

    companion object {
        val Saver = listSaver<ReportBlockState, Any>(
            save = { listOf(it.kind?.name.orEmpty(), it.username, it.opens) },
            restore = { saved ->
                ReportBlockState().apply {
                    kind = (saved[0] as String).takeIf { it.isNotEmpty() }?.let { ReportBlockKind.valueOf(it) }
                    username = saved[1] as String
                    opens = saved[2] as Int
                }
            },
        )
    }
}

@Composable
fun rememberReportBlockState(): ReportBlockState = rememberSaveable(saver = ReportBlockState.Saver) { ReportBlockState() }

/**
 * "Reportar" and "Bloquear" as two quiet links (48 dp tall, like "Eliminar amigo"). [color] is the one of the text on the surface
 * they sit on (light on the green background, dark on a light card).
 */
@Composable
fun ReportBlockLinks(
    username: String,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NightWatchOnBackground,
    arrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    Row(horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        LinkText(R.string.social_report_link, stringResource(R.string.social_report_link_desc, username), color, onReport)
        LinkText(R.string.social_block_link, stringResource(R.string.social_block_link_desc, username), color, onBlock)
    }
}

@Composable
private fun LinkText(@StringRes text: Int, description: String, color: Color, onClick: () -> Unit) {
    Text(
        text = stringResource(text),
        color = color,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/**
 * Draws the dialog that [state] says is open, if any. [onBlocked] runs with the username when the person closes the dialog of a
 * block that went through (the profile of that friend closes, the search result goes away...).
 */
@Composable
fun ReportBlockDialogs(state: ReportBlockState, onBlocked: (username: String) -> Unit = {}) {
    val username = state.username
    when (state.kind) {
        ReportBlockKind.Report -> ReportDialog(username = username, opens = state.opens, onClose = state::close)
        ReportBlockKind.Block -> BlockDialog(
            username = username,
            opens = state.opens,
            onClose = { blocked ->
                state.close()
                if (blocked) onBlocked(username)
            },
        )
        null -> Unit
    }
}

@Composable
private fun ReportDialog(username: String, opens: Int, onClose: () -> Unit) {
    val viewModel: ReportViewModel = viewModel(key = "report-$username-$opens", factory = ReportViewModel.factory(username))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDialogContent(
        uiState = uiState,
        onReason = viewModel::onReasonSelected,
        onNote = viewModel::onNoteChanged,
        onSend = viewModel::onSend,
        onClose = onClose,
    )
}

@StringRes
private fun ReportReason.labelRes(): Int = when (this) {
    ReportReason.InappropriatePhoto -> R.string.social_report_reason_inappropriate_photo
    ReportReason.OffensiveName -> R.string.social_report_reason_offensive_name
    ReportReason.Harassment -> R.string.social_report_reason_harassment
    ReportReason.Other -> R.string.social_report_reason_other
}

/** The form of the report, or its thanks once the server took it. A report in flight cannot be dismissed by a tap outside. */
@Composable
internal fun ReportDialogContent(
    uiState: ReportUiState,
    onReason: (ReportReason) -> Unit,
    onNote: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!uiState.sending) onClose() },
        title = {
            Text(
                if (uiState.sent) {
                    stringResource(R.string.social_report_done_title)
                } else {
                    stringResource(R.string.social_report_title, uiState.username)
                },
            )
        },
        text = {
            if (uiState.sent) {
                Text(stringResource(R.string.social_report_done_body, uiState.username))
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.social_report_intro), fontSize = 13.sp)
                    ReportReason.entries.forEach { reason ->
                        val selected = uiState.reason == reason
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = selected,
                                    enabled = !uiState.sending,
                                    role = Role.RadioButton,
                                    onClick = { onReason(reason) },
                                ),
                        ) {
                            RadioButton(selected = selected, onClick = null, enabled = !uiState.sending)
                            Text(text = stringResource(reason.labelRes()), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedTextField(
                        value = uiState.note,
                        onValueChange = onNote,
                        enabled = !uiState.sending,
                        label = { Text(stringResource(R.string.social_report_note_label)) },
                        supportingText = {
                            Text(
                                text = stringResource(R.string.social_report_note_counter, uiState.noteLength, REPORT_NOTE_MAX_LENGTH),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    uiState.error?.let { InlineError(it) }
                }
            }
        },
        confirmButton = {
            if (uiState.sent) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.social_report_done_close)) }
            } else {
                TextButton(onClick = onSend, enabled = uiState.canSend) {
                    Text(stringResource(if (uiState.sending) R.string.social_report_sending else R.string.social_report_send))
                }
            }
        },
        dismissButton = {
            if (!uiState.sent) {
                TextButton(onClick = onClose, enabled = !uiState.sending) { Text(stringResource(R.string.social_report_cancel)) }
            }
        },
    )
}

@Composable
private fun BlockDialog(username: String, opens: Int, onClose: (blocked: Boolean) -> Unit) {
    val viewModel: BlockViewModel = viewModel(key = "block-$username-$opens", factory = BlockViewModel.factory(username))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BlockDialogContent(uiState = uiState, onConfirm = viewModel::onConfirm, onClose = { onClose(uiState.blocked) })
}

/** What blocking does, with "Bloquear" and "Cancelar"; then the confirmation that it is done. */
@Composable
internal fun BlockDialogContent(uiState: BlockUiState, onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!uiState.blocking) onClose() },
        title = {
            Text(
                if (uiState.blocked) {
                    stringResource(R.string.social_block_done_title)
                } else {
                    stringResource(R.string.social_block_title, uiState.username)
                },
            )
        },
        text = {
            Column {
                Text(
                    if (uiState.blocked) {
                        stringResource(R.string.social_block_done_body, uiState.username)
                    } else {
                        stringResource(R.string.social_block_body)
                    },
                )
                uiState.error?.let { InlineError(it) }
            }
        },
        confirmButton = {
            if (uiState.blocked) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.social_block_done_close)) }
            } else {
                TextButton(onClick = onConfirm, enabled = !uiState.blocking) {
                    Text(stringResource(if (uiState.blocking) R.string.social_block_blocking else R.string.social_block_confirm))
                }
            }
        },
        dismissButton = {
            if (!uiState.blocked) {
                TextButton(onClick = onClose, enabled = !uiState.blocking) { Text(stringResource(R.string.social_block_cancel)) }
            }
        },
    )
}
