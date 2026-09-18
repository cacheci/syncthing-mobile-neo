package moe.https.syncthing.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import moe.https.syncthing.generated.resources.Res
import moe.https.syncthing.generated.resources.logo_qr
import moe.https.syncthing.icon
import moe.https.syncthing.platform.isSystem24HourFormat
import moe.https.syncthing.platform.rememberClipboard
import moe.https.syncthing.ui.model.AppPage
import moe.https.syncthing.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable
import kotlin.enums.EnumEntries

@Composable
fun ValueRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueSingleLine: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AppTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = if (valueSingleLine) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis ,
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
    color: Color = AppTheme.colorScheme.onBackground
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = AppTheme.colorScheme.onSurfaceVariantSummary,
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
    summary: String? = null,
    valueValidator: (String) -> Boolean = { true },
    singleLine: Boolean = true,
    allowEdit: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Row (
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column (modifier = Modifier.weight(0.65f)) {
            Text(
                text = label,
                fontSize = AppTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (valueValidator(value)) AppTheme.colorScheme.onBackground else AppTheme.colorScheme.error,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = AppTheme.textStyles.body2.fontSize,
                    color = AppTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Box (
            modifier = Modifier.weight(0.3f),
        ) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                textStyle = AppTheme.textStyles.main.copy(
                    textAlign = TextAlign.End,
                    color = if (allowEdit) AppTheme.colorScheme.onBackground else AppTheme.colorScheme.onSecondaryContainer
                ),
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                enabled = allowEdit,
            )
            if (!value.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then( if (singleLine) Modifier.basicMarquee() else Modifier ),
                    text = valueLabel,
                    textAlign = TextAlign.End,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    color = AppTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun InfoSwitchCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 10.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
    )
    Card (modifier = modifier) {
        Column(content = { content() })
    }
}

@Composable
internal fun MessageCard(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    isError: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (isError) {
            CardDefaults.defaultColors(
                color = AppTheme.colorScheme.errorContainer,
                contentColor = AppTheme.colorScheme.onErrorContainer,
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
                style = AppTheme.textStyles.title4,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Text(
                text = message,
                color = AppTheme.colorScheme.onSecondaryContainer,
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
    enabled: Boolean = true,
    statusColor: Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        startAction = if (statusColor != null) ({
            Text(
                text = "●",
                color = statusColor,
            )
        }) else null,
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
    content: @Composable () -> Unit = {},
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
            style = AppTheme.textStyles.headline1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = if (isError) { AppTheme.colorScheme.error } else { AppTheme.colorScheme.onSurfaceVariantSummary },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        content()
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
    content: (@Composable () -> Unit)? = null,
) {

    var isEditing by remember { mutableStateOf(false) }
    val valueValid = valueValidator(value)
    val canDelete = onDelete != null && enabled && (!state || !valueValid)

    Column (
        modifier = Modifier.padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                    textStyle = AppTheme.textStyles.main.copy(
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        color = if (enabled) AppTheme.colorScheme.onBackground else AppTheme.colorScheme.onSecondaryContainer
                    ),
                    onValueChange = onValueChange,
                    readOnly = readOnly || state,
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
                        color = AppTheme.colorScheme.onSecondaryContainer,
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

        if (content != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                content()
            }
        } else {
            Box( modifier = Modifier.padding(vertical = 6.dp))
        }

        HorizontalDivider( modifier = Modifier.fillMaxWidth( 0.85f ) )
    }
}

@Composable
internal fun DeleteBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        modifier = modifier
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
                color = if ( enabled ) AppTheme.colorScheme.error else AppTheme.colorScheme.background,
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
internal fun PendingCard(
    title: String,
    content: @Composable () -> Unit,
) {
    var foldContentStatus by rememberSaveable { mutableStateOf(true) }

    Card (
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Column (
            modifier = Modifier.fillMaxWidth().border(
                width = if (isSystemInDarkTheme()) 1.5.dp else 0.dp,
                color = Color(0xFFE18F29),
                shape = RoundedCornerShape(16.dp)
            ),
        ) {
            Row (
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF6C435))
                    .padding(16.dp)
                    .combinedClickable(
                        onLongClick = { },
                        onClick = { foldContentStatus = !foldContentStatus },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("●", color = AppTheme.colorScheme.onPrimary)
                Text(
                    text = title,
                    color = AppTheme.colorScheme.onPrimary,
                )
            }

            AnimatedVisibility(
                visible = foldContentStatus,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                )
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun TimePicker(
    initialHour: Int = 13,
    initialMinute: Int = 30,
    use24h: Boolean? = null,
    onTimeChange: (hour: Int, minute: Int) -> Unit = { _, _ -> },
) {
    val systemUse24h = isSystem24HourFormat()
    val resolvedUse24h = use24h ?: systemUse24h
    var hourValue by rememberSaveable(initialHour) {
        mutableIntStateOf(initialHour.coerceIn(0, 23))
    }
    var minuteValue by rememberSaveable(initialMinute) {
        mutableIntStateOf(initialMinute.coerceIn(0, 59))
    }
    val isPm = hourValue >= 12
    val displayedHour = when (val hourInHalfDay = hourValue % 12) {
        0 -> 12
        else -> hourInHalfDay
    }

    fun updateHour(newHour: Int) {
        hourValue = newHour
        onTimeChange(hourValue, minuteValue)
    }

    fun updateMinute(newMinute: Int) {
        minuteValue = newMinute
        onTimeChange(hourValue, minuteValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!resolvedUse24h) {
            NumberPicker(
                value = if (isPm) 1 else 0,
                onValueChange = { selectedPeriod ->
                    updateHour(hourValue % 12 + if (selectedPeriod == 1) 12 else 0)
                },
                range = 0..1,
                label = { if (it == 0) "AM" else "PM" },
                wrapAround = false,
                textStyle = AppTheme.textStyles.title3,
                modifier = Modifier.weight(0.6f),
            )
        }
        NumberPicker(
            value = if (resolvedUse24h) hourValue else displayedHour,
            onValueChange = { selectedHour ->
                updateHour(
                    if (resolvedUse24h) {
                        selectedHour
                    } else {
                        selectedHour % 12 + if (isPm) 12 else 0
                    },
                )
            },
            range = if (resolvedUse24h) 0..23 else 1..12,
            label = { it.toString().padStart(2, '0') },
            wrapAround = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        NumberPicker(
            value = minuteValue,
            onValueChange = ::updateMinute,
            range = 0..59,
            label = { it.toString().padStart(2, '0') },
            wrapAround = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun CheckableRow(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    state: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row (
        modifier = modifier
            .fillMaxWidth()
            .clickable( enabled = enabled, onClick = onClick )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column ( modifier = Modifier.weight(1f) ) {
            Text(
                text = title,
                color = if (enabled) AppTheme.colorScheme.onBackground else AppTheme.colorScheme.disabledOnSurface
            )
            summary?.let {
                Text(
                    text = it,
                    color = AppTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        Checkbox(
            state = ToggleableState(state),
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
internal fun AppNavigationBar(
    entries: EnumEntries<AppPage>,
    visiblePages: Set<AppPage>,
    currentPage: AppPage,
    floating: Boolean = false, //TODO
    onNavigationBarItemClick: (AppPage) -> Unit = {},
    navbarColor: Color = AppTheme.colorScheme.background,
    defaultWindowInsetsPadding: Boolean = true,
    backdrop: LayerBackdrop? = null,
) {
    if (!floating) {
        NavigationBar(
            modifier = Modifier.barBackdropBlur(backdrop, RectangleShape, navbarColor),
            color = if (backdrop != null) Color.Transparent else navbarColor,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        ) {
            entries
                .filter(visiblePages::contains)
                .forEach { page ->
                    NavigationBarItem(
                        selected = currentPage == page,
                        onClick = { onNavigationBarItemClick(page) },
                        icon = page.icon,
                        label = page.title,
                    )
                }
        }
    } else {
        FloatingNavigationBar(
            modifier = Modifier.barBackdropBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius),
                tint = navbarColor,
            ),
            color = if (backdrop != null) Color.Transparent else navbarColor,
            shadowElevation = 0.dp,
            showDivider = true,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            bottomContent = entries
                .filter(visiblePages::contains)
                .map { page ->
                    @Composable {
                        FloatingNavItem(
                            selected = currentPage == page,
                            onClick = { onNavigationBarItemClick(page) },
                            icon = page.icon,
                            label = page.title,
                        )
                    }
                }
        )
    }
}
