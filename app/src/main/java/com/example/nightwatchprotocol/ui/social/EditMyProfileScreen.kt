package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme

/**
 * "Editar tu perfil" (Figma SOCIAL UPDATE 2653:611): the eyes next to Insignias, Estadísticas and Historial de batallas, the pencil
 * that opens the picker of the background, and "GUARDAR CAMBIOS" and "CANCELAR" fixed at the bottom. Everything is a draft until
 * "GUARDAR CAMBIOS"; "CANCELAR", the "X" and the back button drop it without saving.
 */
@Composable
fun EditMyProfileScreen(
    onClose: () -> Unit,
    viewModel: EditMyProfileViewModel = viewModel(factory = EditMyProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.load()
        viewModel.events.collect { event ->
            when (event) {
                EditMyProfileEvent.Saved, EditMyProfileEvent.Discarded -> onClose()
            }
        }
    }

    SocialProfileScreen(
        title = stringResource(R.string.social_myprofile_title),
        onClose = onClose,
        profileBg = uiState.draft.profileBg,
    ) {
        EditMyProfileContent(
            uiState = uiState,
            onRetry = viewModel::load,
            onToggle = viewModel::onToggle,
            onPickerOpen = viewModel::onPickerOpened,
            onPickerDismiss = viewModel::onPickerDismissed,
            onSelectBackground = viewModel::onBackgroundSelected,
            onSave = viewModel::onSaveClicked,
            onCancel = viewModel::onCancelClicked,
        )
    }
}

@Composable
internal fun ColumnScope.EditMyProfileContent(
    uiState: EditMyProfileUiState,
    onRetry: () -> Unit,
    onToggle: (ProfileSection) -> Unit,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onSelectBackground: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            uiState.loadError?.let { ErrorNotice(it, onRetry) }
            val data = uiState.data
            if (data == null) {
                if (uiState.loading) SocialLoading()
            } else {
                ProfileSections(
                    // The editor draws every section; the closed ones are dimmed, not removed.
                    content = data.toProfileContent(forEditor = true),
                    avatar = { MyAvatar(data) },
                    eyes = ProfileEyes(
                        badgesVisible = uiState.draft.showBadges,
                        statsVisible = uiState.draft.showStats,
                        historyVisible = uiState.draft.showHistory,
                        onToggle = onToggle,
                    ),
                )
            }
        }
        if (uiState.loaded) {
            ProfileBackgroundPicker(
                selected = uiState.draft.profileBg,
                open = uiState.pickerOpen,
                onOpen = onPickerOpen,
                onDismiss = onPickerDismiss,
                onSelect = onSelectBackground,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp),
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        uiState.saveError?.let { InlineError(it, Modifier.padding(bottom = 6.dp)) }
        NightWatchButton(
            text = stringResource(if (uiState.saving) R.string.social_myprofile_saving else R.string.social_myprofile_save),
            onClick = onSave,
            enabled = uiState.loaded && !uiState.saving,
        )
        NightWatchButton(
            text = stringResource(R.string.social_myprofile_cancel),
            onClick = onCancel,
            enabled = !uiState.saving,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun previewState(draft: ProfileDisplaySettings, pickerOpen: Boolean = false) = EditMyProfileUiState(
    loading = false,
    data = MyProfileData(
        username = "Juan",
        avatarKey = null,
        avatarVersion = null,
        display = draft,
        fighterLevel = 1,
        streakDays = 2,
        trainedToday = true,
        exercises = 47,
        labs = 5,
        badges = emptyList(),
        stats = BattleStats(15, 10, 4, 1),
        history = listOf(ProfileBattleRow(BattleOutcome.Win, "Mateo", 2), ProfileBattleRow(BattleOutcome.Loss, "Sofía", 6)),
    ),
    draft = draft,
    pickerOpen = pickerOpen,
)

@Composable
private fun PreviewEdit(draft: ProfileDisplaySettings) {
    NightWatchProtocolTheme {
        SocialProfileScreen(title = "Tu perfil", onClose = {}, profileBg = draft.profileBg) {
            EditMyProfileContent(previewState(draft), {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Editar: todo visible", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun EditAllVisiblePreview() = PreviewEdit(ProfileDisplaySettings())

@Preview(name = "Editar: estadísticas ocultas y fondo 2", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun EditHiddenPreview() = PreviewEdit(ProfileDisplaySettings(profileBg = 2, showStats = false))
