package moe.https.syncthing.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform

fun isBarBlurSupported(): Boolean = isRuntimeShaderSupported()

@Composable
fun rememberBarBackdrop(enabled: Boolean = true): LayerBackdrop? {
    if (!enabled || !isBarBlurSupported()) return null

    val backgroundColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
}

fun Modifier.barBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

@Composable
internal fun Modifier.barBackdropBlur(
    backdrop: LayerBackdrop?,
    shape: Shape,
    tint: Color,
): Modifier {
    if (backdrop == null) return this

    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(tint.copy(alpha = 0.72f))),
        saturation = 1.1f,
    )
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = 20f,
        colors = blurColors,
    )
}

@Composable
fun BlurredSmallTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    color: Color = MiuixTheme.colorScheme.surface,
    defaultWindowInsetsPadding: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    SmallTopAppBar(
        title = title,
        modifier = modifier.barBackdropBlur(backdrop, RectangleShape, color),
        subtitle = subtitle,
        color = if (backdrop != null) Color.Transparent else color,
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        navigationIcon = navigationIcon,
        actions = actions,
        bottomContent = bottomContent,
    )
}

@Composable
fun AdaptiveTopAppBar(
    title: String,
    showTopAppBar: Boolean,
    isWideScreen: Boolean,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop? = null,
    subtitle: String = "",
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (showTopAppBar) {
        if (isWideScreen) {
            BlurredSmallTopAppBar(
                title = title,
                subtitle = subtitle,
                color = color,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                defaultWindowInsetsPadding = false,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = bottomContent,
            )
        } else {
            TopAppBar(
                title = title,
                modifier = Modifier.barBackdropBlur(backdrop, RectangleShape, color),
                subtitle = subtitle,
                color = if (backdrop != null) Color.Transparent else color,
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

@Composable
fun FloatingNavigationBar(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = FloatingToolbarDefaults.CornerRadius,
    horizontalAlignment: Alignment.Horizontal = CenterHorizontally,
    horizontalOutSidePadding: Dp = FloatingNavigationBarDefaults.HorizontalOutSidePadding,
    shadowElevation: Dp = FloatingNavigationBarDefaults.ShadowElevation,
    showDivider: Boolean = false,
    defaultWindowInsetsPadding: Boolean = true,
    bottomContent: List<@Composable () -> Unit>,
) {
    val shape = RoundedCornerShape(cornerRadius)

    val navBarBottomPadding = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPaddingValue = when (platform()) {
        Platform.IOS -> 36.dp

        else -> {
            if (navBarBottomPadding != 0.dp) 16.dp + navBarBottomPadding else 26.dp
        }
    }

    val captionBarBottomPaddingValue = WindowInsets.captionBar.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val animatedCaptionBarHeight by animateDpAsState(
        targetValue = if (captionBarBottomPaddingValue > 0.dp) captionBarBottomPaddingValue else 0.dp,
        animationSpec = tween(durationMillis = 300),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (horizontalAlignment == Alignment.Start) horizontalOutSidePadding else 20.dp,
                end = if (horizontalAlignment == Alignment.End) horizontalOutSidePadding else 20.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(bottom = bottomPaddingValue)
                .defaultMinSize(minHeight = 52.dp)
                .then(
                    if (defaultWindowInsetsPadding) {
                        Modifier.padding(bottom = animatedCaptionBarHeight)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (showDivider) {
                        Modifier
                            .squircleBackground(
                                color = MiuixTheme.colorScheme.dividerLine,
                                cornerRadius = cornerRadius,
                            )
                            .padding(0.75.dp)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (shadowElevation > 0.dp) {
                        Modifier.dropShadow(
                            shape = shape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = 0.2f,
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .squircleBackground(color = color, cornerRadius = cornerRadius)
                .then(modifier)
                .padding(horizontal = FloatingNavigationBarDefaults.HorizontalPadding)
                .align(horizontalAlignment)
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume click */ }
                },
            horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomContent.forEach {
                Box (
                    modifier = Modifier
                        .height(NavigationBarDefaults.ItemHeight)
                        .weight(1f)
                ){ it() }
            }
        }
    }
}

@Composable
internal fun FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> if (selected) {
            onSurfaceContainerColor.copy(alpha = NavigationBarDefaults.SelectedPressedAlpha)
        } else {
            onSurfaceContainerColor.copy(alpha = NavigationBarDefaults.UnselectedPressedAlpha)
        }

        selected -> onSurfaceContainerColor

        else -> onSurfaceContainerColor.copy(NavigationBarDefaults.UnselectedAlpha)
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = modifier
            .fillMaxSize()
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            ),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        NavigationItemIcon(
            badge = badge,
            modifier = Modifier.padding(top = NavigationBarDefaults.IconTopPadding),
        ) {iconModifier ->
            Image(
                modifier = iconModifier.size(NavigationBarDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
            )
        }
        Text(
            modifier = Modifier.padding(bottom = NavigationBarDefaults.BottomPadding),
            text = label,
            color = tint,
            textAlign = TextAlign.Center,
            fontSize = NavigationBarDefaults.LabelFontSize,
            fontWeight = fontWeight,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NavigationItemIcon(
    badge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BadgedBox(modifier = modifier, badge = { badge?.invoke() }) { content(Modifier) }
}

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }
