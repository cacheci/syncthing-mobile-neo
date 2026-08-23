package moe.https.syncthing.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.generated.resources.Res
import moe.https.syncthing.generated.resources.logo_qr
import moe.https.syncthing.platform.rememberClipboard
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
fun ValueRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(0.5f)
                .clickable(
                    enabled = onClick != null,
                    onClick = { onClick?.invoke() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ),
        )
    }
}

@Composable
internal fun MultipleValueRow(
    modifier: Modifier = Modifier,
    label: String,
    values: List<String>,
    onClick: (() -> Unit)? = null,
    textAlign: TextAlign = TextAlign.End,
    color: Color = MiuixTheme.colorScheme.onBackground
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.35f),
        )
        Column(
            modifier = Modifier
                .weight(0.65f)
                .clickable(
                    enabled = onClick != null,
                    onClick = { onClick?.invoke() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ),
        ) {
            if (values.count() > 1) {
                values.forEach { values ->
                    Row {
                        Text(
                            text = "·",
                            modifier = Modifier.weight(0.05f),
                        )
                        Text(
                            text = values.toCharArray().joinToString("\u200B"),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.95f),
                        )
                    }
                }
            } else {
                Row {
                    Box(modifier = Modifier.weight(0.02f))
                    Text(
                        text = values[0],
                        textAlign = textAlign,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.98f),
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InputValueRow(
    modifier: Modifier = Modifier,
    label: String,
    valueLabel: String,
    value: String,
    onValueChange: ((String) -> Unit),
    singleLine: Boolean = true,
    allowEdit: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Row (
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.5f),
            style = MiuixTheme.textStyles.main.copy(
                fontWeight = FontWeight.Medium,
            )
        )
        Box (
            modifier = Modifier.weight(0.4f),
        ) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                textStyle = MiuixTheme.textStyles.main.copy(
                    textAlign = TextAlign.End,
                    color = if (allowEdit) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSecondaryContainer
                ),
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                enabled = allowEdit,
            )
            if (!value.isNotEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = valueLabel,
                    textAlign = TextAlign.End,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun InfoSwitchCard(
    title: String,
    content: @Composable () -> Unit,
) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 10.dp),
    )
    Card {
        Column(content = { content() })
    }
}

@Composable
internal fun MessageCard(
    title: String,
    message: String,
    isError: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
        colors = if (isError) {
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.defaultColors()
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Text(
                text = message,
                color = MiuixTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
internal fun InfoSwitch(
    title: String,
    summary: String ?= null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
internal fun CoreNotReadyTakePlace(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = if (isError) { MiuixTheme.colorScheme.error } else { MiuixTheme.colorScheme.onSurfaceVariantSummary },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun DeviceShareOverlayDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    deviceID: String,
) {
    val clipboard = rememberClipboard()

    OverlayDialog(
        show = show,
        title = "分享设备",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        content = {
            Column (
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box (
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(140.dp)
                        .height(140.dp)
                        .background(
                            Color(0xFFFFFFFF),
                            shape = RoundedCornerShape(8.dp),
                        ),
                ) {
                    Image(
                        modifier = Modifier.padding(10.dp).size(120.dp),
                        painter = rememberQrCodePainter(
                            data = deviceID,
                            logoPainter = painterResource(Res.drawable.logo_qr),
                            logoSize = 0.2f,
                        ),
                        contentDescription = deviceID,
                    )
                }
                Text(
                    modifier = Modifier.padding(
                        vertical = 10.dp,
                        horizontal = 20.dp,
                    ),
                    text = deviceID,
                    textAlign = TextAlign.Center
                )
                Row {
                    TextButton(
                        modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
                        text = "复制",
                        onClick = {
                            clipboard.copy(deviceID)
                            onDismissRequest()
                        },
                    )
                    TextButton(
                        modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
                        text = "确定",
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    )
}

@Composable
internal fun CheckableInputValueRow(
    state: Boolean,
    value: String,
    valueLabel: String = "",
    onValueChange: (String) -> Unit,
    valueValidator: (String) -> Boolean,
    onStateChange: () -> Unit,
    onDelete: (() -> Unit)?,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {

    var isEditing by remember { mutableStateOf(false) }
    val valueValid = valueValidator(value)
    val canDelete = onDelete != null && enabled && (!state || !valueValid)

    Column ( horizontalAlignment = Alignment.CenterHorizontally ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                state = if (!valueValid) ToggleableState.Indeterminate else ToggleableState(state),
                onClick = onStateChange,
                enabled = enabled && valueValid,
            )

            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    modifier = Modifier.onFocusChanged { focusState ->
                        isEditing = focusState.isFocused
                    },
                    value = value,
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSecondaryContainer
                    ),
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    enabled = enabled || isEditing,
                )
                if (!value.isNotEmpty()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = valueLabel,
                        textAlign = TextAlign.Start,
                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            AnimatedVisibility(
                visible = canDelete,
                enter = scaleIn(animationSpec = tween(durationMillis = 300)) + slideInHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetX = { fullWidth -> fullWidth / 2 },
                ),
                exit = scaleOut(animationSpec = tween(durationMillis = 300)) + slideOutHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    targetOffsetX = { fullWidth -> fullWidth / 2 },
                ),
            ) {
                DeleteBox(enabled = true, onDelete = { onDelete?.invoke() })
            }
        }

        HorizontalDivider( modifier = Modifier.fillMaxWidth( 0.85f ) )
    }
}

@Composable
internal fun DeleteBox(
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    val sinkFeedback = remember {
        SinkFeedback(
            sinkAmount = 0.85f,
            animationSpec = spring(0.99f, 986.96f)
        )
    }
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.Center)
            .requiredSize(26.dp)
            .pressable(
                interactionSource = remember { MutableInteractionSource() },
                indication = sinkFeedback,
                enabled = enabled,
                delay = null,
            )
            .clip(CircleShape)
            .background(
                color = if ( enabled ) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.background,
                shape = CircleShape,
            )
            .triStateToggleable(
                state = ToggleableState(true),
                onClick = {
                    onDelete()
                    hapticFeedback.performHapticFeedback(
                        HapticFeedbackType.ToggleOff,
                    )
                },
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = enabled
        ) {
            if ( enabled ) {
                Box (
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight(0.1f)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
fun CoreState.displayColor(): Color = when (this) {
    CoreState.RUNNING -> StatusColor.OK.color
    CoreState.FAILED -> StatusColor.FAIL.color
    CoreState.STARTING,
    CoreState.STOPPING,
    CoreState.INSTALLING -> StatusColor.PENDING.color
    else -> StatusColor.DOWN.color
}

internal enum class StatusColor ( val color: Color ){
    OK(Color(0xFF2E7D32)),
    FAIL(Color(0xFFFF3728)),
    PENDING(Color(0xFFB26A00)),
    DOWN(Color(0xFF666666)),
}