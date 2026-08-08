package moe.https.syncthing.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AdaptiveTopAppBar(
    title: String,
    showTopAppBar: Boolean,
    isWideScreen: Boolean,
    scrollBehavior: ScrollBehavior,
    subtitle: String = "",
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (showTopAppBar) {
        if (isWideScreen) {
            SmallTopAppBar(
                title = title,
                subtitle = subtitle,
                color = color,
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = bottomContent,
            )
        } else {
            TopAppBar(
                title = title,
                subtitle = subtitle,
                color = color,
                scrollBehavior = scrollBehavior,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = bottomContent,
            )
        }
    }
}

@Composable
internal fun TextWithOptionField(
    value: String,
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = DpSize(16.dp, 16.dp),
    colors: TextFieldColors = TextFieldColors(
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
        labelColor = MiuixTheme.colorScheme.onSecondaryContainer,
        borderColor = MiuixTheme.colorScheme.primary,
    ),
    cornerRadius: Dp = 16.dp,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxHeight: Dp? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(colors.borderColor),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelState = remember(value, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> LabelAnimState.Hidden
            useLabelAsPlaceholder && value.isNotEmpty() -> LabelAnimState.Placeholder
            value.isNotEmpty() -> LabelAnimState.Floating
            else -> LabelAnimState.Normal
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val contentColor = LocalContentColor.current
    val resolvedTextStyle = remember(textStyle, contentColor) {
        val textColor = textStyle.color.takeOrElse { contentColor }
        textStyle.copy(textColor)
    }

    val isDropdownExpanded = remember { mutableStateOf(false) }
    val actualDropdownEnabled = enabled && items.isNotEmpty()
    val actionColor = if (actualDropdownEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }
    val entry = remember(
        items,
        selectedIndex,
        onSelectedIndexChange,
    ) {
        DropdownEntry(
            items.mapIndexed { index, item ->
                DropdownItem(
                    text = item,
                    selected = index == selectedIndex,
                    onClick = { onSelectedIndexChange(index) },
                )
            },
        )
    }
    val itemsNotEmpty = entry.items.isNotEmpty()
    val borderWidthState = animateDpAsState(if (isFocused) 2.dp else 0.dp)
    val borderColorState = animateColorAsState(if (isFocused) colors.borderColor else colors.backgroundColor)
    val labelAnim = animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> -insideMargin.height / 2
            LabelAnimState.Placeholder, LabelAnimState.Normal, LabelAnimState.Hidden -> 0.dp
        },
    )
    val labelFontSize by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> 10.dp else -> 17.dp
        },
    )
    val hasLeadingIcon = leadingIcon != null
    val hasTrailingIcon = trailingIcon != null
    val paddingModifier = remember(hasLeadingIcon, hasTrailingIcon, insideMargin) {
        when {
            !hasLeadingIcon && !hasTrailingIcon -> Modifier.padding(insideMargin.width, vertical = insideMargin.height)
            !hasLeadingIcon -> Modifier.padding(start = insideMargin.width).padding(vertical = insideMargin.height)
            !hasTrailingIcon -> Modifier.padding(end = insideMargin.width).padding(vertical = insideMargin.height)
            else -> Modifier.padding(vertical = insideMargin.height)
        }
    }

    BasicTextField(
        value = value,
        onValueChange = currentOnValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        minLines = 1,
        visualTransformation = visualTransformation,
        onTextLayout = currentOnTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = @Composable { innerTextField ->
            Column (
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Box(
                    modifier = Modifier
                        .squircleBackground(
                            color = colors.backgroundColor,
                            cornerRadius = cornerRadius
                        )
                        .squircleBorder(
                            width = { borderWidthState.value },
                            color = { borderColorState.value },
                            cornerRadius = cornerRadius,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingIcon?.invoke()
                        Box(
                            modifier = Modifier.weight(1f).then(paddingModifier),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            if (labelState != LabelAnimState.Hidden && labelState != LabelAnimState.Placeholder) {
                                Text(
                                    text = label,
                                    fontSize = labelFontSize.value.sp,
                                    color = colors.labelColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.offset {
                                        IntOffset(
                                            0,
                                            labelAnim.value.roundToPx()
                                        )
                                    },
                                    textAlign = TextAlign.Start,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .offset(y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(modifier = Modifier.weight(0.65f)) {
                                    innerTextField()
                                }

                                VerticalDivider(modifier = Modifier.fillMaxHeight())

                                Row(
                                    modifier = Modifier
                                        .weight(0.2f)
                                        .fillMaxHeight()
                                        .combinedClickable(
                                            enabled = actualDropdownEnabled,
                                            role = Role.DropdownList,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { setExpanded(!isDropdownExpanded.value) },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    if (itemsNotEmpty) {
                                        val text = entry.items.firstOrNull { it.selected }?.text
                                        if (!text.isNullOrEmpty()) {
                                            Text(
                                                text = text,
                                                modifier = Modifier
                                                    .padding(end = 12.dp)
                                                    .weight(1f, fill = false),
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = actionColor,
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }

                                    DropdownArrowEndAction(actionColor = actionColor)

                                    if (itemsNotEmpty) {
                                        OverlayDropdownPopup(
                                            entry = entry,
                                            show = isDropdownExpanded.value,
                                            onDismiss = { setExpanded(false) },
                                            onDismissFinished = {},
                                            maxHeight = maxHeight,
                                            dropdownColors = dropdownColors,
                                            renderInRootScaffold = renderInRootScaffold,
                                            collapseOnSelection = collapseOnSelection,
                                        )
                                    }
                                }
                            }
                        }
                        trailingIcon?.invoke()
                    }
                }
            }
        },
    )
}

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }
