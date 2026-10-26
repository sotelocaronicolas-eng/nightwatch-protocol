package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme

/**
 * "Tu perfil" (Figma SOCIAL UPDATE 2653:543): the same sections friends see, from the person's own data, with the button
 * "EDITAR PERFIL" fixed at the bottom. The profile is read again when the screen comes back, so the edits show at once.
 */
@Composable
fun MyProfileScreen(
    onClose: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MyProfileViewModel = viewModel(factory = MyProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SocialProfileScreen(
        title = stringResource(R.string.social_myprofile_title),
        onClose = onClose,
        profileBg = uiState.data?.display?.profileBg ?: 0,
    ) {
        MyProfileContent(uiState = uiState, onRetry = viewModel::refresh, onEdit = onEdit)
    }
}

@Composable
internal fun ColumnScope.MyProfileContent(uiState: MyProfileUiState, onRetry: () -> Unit, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        uiState.loadError?.let { ErrorNotice(it, onRetry) }
        val data = uiState.data
        if (data == null) {
            if (uiState.loading) SocialLoading()
        } else {
            ProfileSections(
                content = data.toProfileContent(),
                avatar = { MyAvatar(data) },
            )
        }
    }
    NightWatchButton(
        text = stringResource(R.string.social_myprofile_edit),
        onClick = onEdit,
        enabled = uiState.loaded,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
internal fun MyAvatar(data: MyProfileData) {
    SocialPhotoAvatar(
        avatarKey = data.avatarKey,
        avatarVersion = data.avatarVersion,
        emoji = stringResource(R.string.social_icon_person),
        size = 130.dp,
        withWizardGear = false,
        contentDescription = stringResource(R.string.social_avatar_of, data.username),
    )
}

private fun previewData(display: ProfileDisplaySettings = ProfileDisplaySettings()) = MyProfileData(
    username = "Juan",
    avatarKey = null,
    avatarVersion = null,
    display = display,
    fighterLevel = 1,
    streakDays = 2,
    trainedToday = true,
    exercises = 47,
    labs = 5,
    badges = emptyList(),
    stats = BattleStats(15, 10, 4, 1),
    history = listOf(
        ProfileBattleRow(BattleOutcome.Win, "Mateo", 2),
        ProfileBattleRow(BattleOutcome.Loss, "Sofía", 6),
    ),
)

@Composable
private fun PreviewMyProfile(display: ProfileDisplaySettings) {
    NightWatchProtocolTheme {
        SocialProfileScreen(title = "Tu perfil", onClose = {}, profileBg = display.profileBg) {
            MyProfileContent(uiState = MyProfileUiState(loading = false, data = previewData(display)), onRetry = {}, onEdit = {})
        }
    }
}

@Preview(name = "Tu perfil", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MyProfilePreview() = PreviewMyProfile(ProfileDisplaySettings())

@Preview(name = "Tu perfil con fondo y apartados ocultos", showBackground = true, widthDp = 412, heightDp = 917)
@Composable
private fun MyProfileHiddenPreview() = PreviewMyProfile(ProfileDisplaySettings(profileBg = 4, showBadges = false, showStats = false))
