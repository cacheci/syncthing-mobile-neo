package moe.https.syncthing.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.https.syncthing.ui.component.MessageCard
import moe.https.syncthing.ui.component.RuntimeCard
import moe.https.syncthing.ui.component.StatusCard
import moe.https.syncthing.ui.model.CoreUiState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun CoreScreen(
    uiState: CoreUiState,
    snackbarHostState: SnackbarHostState,
    onStartAction: () -> Unit,
    onImportCore: () -> Unit,
    modifier: Modifier = Modifier,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusCard(
            uiState,
            developerModeEnabled = developerModeEnabled,
            onModifyDeveloperMode = onModifyDeveloperMode,
            snackbarHostState = snackbarHostState
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = uiState.actionBtnText,
                onClick = onStartAction,
                enabled = uiState.canAction,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (uiState.version == null) "导入 arm64 核心" else "更新核心",
                onClick = onImportCore,
                enabled = uiState.canImportCore,
                modifier = Modifier.weight(1f),
            )
        }

        RuntimeCard(uiState)

        uiState.lastError?.let { message ->
            MessageCard(
                title = "错误",
                message = message,
                isError = true,
            )
        }
    }
}
