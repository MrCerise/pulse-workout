package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseMotion
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseElevation
import com.pulse.intervalcoach.ui.theme.LocalPulseLayout
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Surfaces, headers and rows — the structural half of the Momentum design system.
 *
 * The rules these components encode:
 *
 *  - A surface is separated from the canvas by its **fill, a hairline and a whisper of shadow**.
 *    Cards float just enough to feel touchable; only cards, menus, dialogs, snackbars, the phone
 *    navigation pill and the hero panel are allowed any elevation at all.
 *  - Radii are generous: 16 dp panels and controls, 22 dp cards, 30 dp sheets. The silhouette is a
 *    soft rectangle, which is what keeps a dense tool friendly.
 *  - A list row is 56 dp tall and its tap target is the whole row.
 *  - Interactive surfaces answer the pointer: a pressed or hovered card lifts — a brighter fill,
 *    a longer shadow — so the affordance is visible before the tap lands.
 */

/**
 * A card. Use it for anything that groups related content: a workout, a stat cluster, a form
 * section. Pass [onClick] to make the whole card a single tap target (the ripple is clipped to the
 * card's radius).
 *
 * [tint] washes the card in a colour — a category hue, a phase tint — without turning it into a
 * saturated block, which is how a screen with eight category cards stays calm.
 */
@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = LocalPulseColors.current.surface,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    val shape = LocalPulseShapes.current.card
    val border = BorderStroke(dimens.borderWidth, colors.border)
    val background = tint?.let { wash(containerColor, it) } ?: containerColor
    val body: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) { content() }
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val lift = pressed || hovered
        val fill by animateColorAsState(
            animationSpec = tween(LocalPulseMotion.current.fast()),
            targetValue = if (lift) wash(background, colors.textPrimary, 0.05f) else background,
            label = "cardFill",
        )
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            // A clickable card lifts: one elevation step of shadow, answered by the brighter fill.
            modifier = modifier
                .fillMaxWidth()
                .shadow(if (lift) elevation.level2 else elevation.level1, shape),
            shape = shape,
            color = fill,
            contentColor = colors.textPrimary,
            border = border,
        ) { body() }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation.level1, shape),
            shape = shape,
            color = background,
            contentColor = colors.textPrimary,
            border = border,
        ) { body() }
    }
}

/**
 * The ink panel: a deliberately dark surface that stays dark in **both** themes.
 *
 * It is the one place a screen raises its voice, which is why it is used twice in the whole app —
 * the Today hero and the session summary banner. It is a feature panel, not a card: ink, a hairline,
 * and a single volt wash rising from the bottom edge — the brand colour as light, not as a block.
 */
@Composable
fun PulseHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.cardPadding),
    /**
     * The smallest the panel may be. A hero that grows with the window keeps its presence on a
     * tablet instead of shrinking into a strip; it never shrinks the content, only the air.
     */
    minHeight: Dp? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    val shape = LocalPulseShapes.current.card
    val body: @Composable () -> Unit = {
        Box {
            // The volt wash: a soft accent glow rising from the lower edge, at a fixed alpha so the
            // panel reads identically in every theme. Text is never drawn over its brightest band.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to colors.brand.copy(alpha = 0.16f),
                        ),
                    ),
            )
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = modifier
                .fillMaxWidth()
                .then(if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier)
                .shadow(elevation.level2, shape),
            shape = shape,
            color = colors.heroSurface,
            contentColor = colors.heroText,
            border = BorderStroke(dimens.borderWidth, colors.heroMuted.copy(alpha = 0.22f)),
        ) { body() }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier)
                .shadow(elevation.level2, shape),
            shape = shape,
            color = colors.heroSurface,
            contentColor = colors.heroText,
            border = BorderStroke(dimens.borderWidth, colors.heroMuted.copy(alpha = 0.22f)),
        ) { body() }
    }
}

/**
 * A well: a surface *below* the canvas, used to nest a group inside a card without adding another
 * border. Where [PulseCard] steps up the ramp, [PulsePanel] steps down, so nesting reads as nesting
 * rather than as two competing panels.
 */
@Composable
fun PulsePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.m),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.panel,
        color = colors.surfaceSunken,
        contentColor = colors.textPrimary,
        border = BorderStroke(LocalPulseDimens.current.borderWidth, colors.border),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Section label. A quiet, tight header for a group of content: the 11 sp section face set in
 * uppercase with a touch of tracking — a caption that behaves like a tab in a notebook — with an
 * optional count on the trailing edge and an optional quiet action ("See all") in the accent, so a
 * section header is never followed by a lonely button on its own line.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.l, bottom = dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = com.pulse.intervalcoach.ui.theme.PulseType.SectionLabel,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(dimens.s))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f, fill = true))
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = LocalPulseShapes.current.control,
                color = Color.Transparent,
                contentColor = colors.accent,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = dimens.s, vertical = dimens.xs),
                )
            }
        }
    }
}

/** A one-pixel separator between rows inside a surface. */
@Composable
fun PulseDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = LocalPulseDimens.current.borderWidth,
        color = LocalPulseColors.current.border,
    )
}

/**
 * A generic information row: an optional leading glyph in a tinted tile, a title, a supporting line
 * and trailing content. Used for settings entries, workout rows and history rows so they all share
 * one rhythm and one density.
 */
@Composable
fun PulseListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    leadingContainer: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowHeight)
                .padding(vertical = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                // The same icon tile the cards and the page headers draw: one size, one radius, one
                // tint treatment, wherever a row identifies itself.
                PulseIconTile(
                    icon = leadingIcon,
                    tint = leadingTint,
                    container = leadingContainer,
                    size = PulseIconTileSize.INLINE,
                )
                Spacer(Modifier.width(dimens.m))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(dimens.s))
                trailing()
            }
        }
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val fill by animateColorAsState(
            animationSpec = tween(LocalPulseMotion.current.fast()),
            targetValue = if (pressed || hovered) colors.surfaceHover else Color.Transparent,
            label = "rowFill",
        )
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = modifier.fillMaxWidth(),
            color = fill,
            contentColor = colors.textPrimary,
        ) { row() }
    } else {
        Box(modifier.fillMaxWidth()) { row() }
    }
}

/**
 * The shell's breadcrumb bar.
 *
 * It is the *only* horizontal chrome in the app: one 52 dp row, pinned above the content, carrying
 * where you are on the left and the global controls on the right. Pushed destinations get a back
 * chevron and the section they came from, which is why no screen has to build a title bar of its
 * own — the shell knows the navigation stack, the page only has to say what it *is* (see
 * [PulsePageHeader]).
 *
 * The status bar inset belongs to this bar, not to the pages below it, so a screen never has to
 * reason about insets.
 */
@Composable
fun PulseBreadcrumbBar(
    title: String,
    modifier: Modifier = Modifier,
    parent: String? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(dimens.topBarHeight)
                .padding(end = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(
                    modifier = Modifier.padding(start = dimens.m),
                    contentAlignment = Alignment.Center,
                ) { leading() }
                Spacer(Modifier.width(dimens.m))
            }
            if (leading == null) {
                Spacer(Modifier.width(dimens.l))
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (parent != null) {
                    Text(
                        text = parent,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.textDisabled,
                        modifier = Modifier
                            .padding(horizontal = dimens.xs)
                            .size(14.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            actions()
        }
        PulseDivider()
    }
}

/**
 * The page header: the way back, the H1, the one-line description and the actions of a page.
 *
 * The title is **centred** on the page — every destination's headline sits in the same optical
 * place, from Progress and Settings to a workout named "5K Race Run", with the page actions
 * floating at the end of the same band. The back link is deliberately *in the page* rather than in
 * the shell: a page knows whether leaving it needs a confirmation (an unsaved builder does), and
 * the shell does not.
 *
 * Centring the title and floating the actions at the end of the same band are two ideas that
 * fight each other on a narrow screen, so the header measures the action cluster and keeps the
 * title clear of it: the title is inset by the cluster's width on **both** sides, which leaves it
 * centred on the page and gives the actions their own space. When the cluster is too wide for
 * that — three icon buttons on a phone — the actions move to a row of their own above the title
 * rather than squeezing it.
 */
@Composable
fun PulsePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth().padding(top = dimens.m, bottom = dimens.l)) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                shape = LocalPulseShapes.current.control,
                color = Color.Transparent,
                contentColor = colors.textSecondary,
            ) {
                Row(
                    modifier = Modifier
                        .height(dimens.iconButtonSize)
                        .padding(end = dimens.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(dimens.xs))
                    Text(
                        text = backLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(dimens.s))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageWidth = maxWidth
            // Read once, in a composable context: the size callback is not one.
            val density = LocalDensity.current
            // The width of the action cluster, measured once it has been laid out. The cluster is
            // always measured *as its content* — in both arrangements — so that a window which
            // grows re-reads the same number and can un-stack itself again.
            var actionsWidth by remember { mutableStateOf(0.dp) }
            // A cluster wider than a quarter of the page would squeeze the title to a stub, so it
            // takes a row of its own instead.
            val stacked = trailing != null && pageWidth > 0.dp && actionsWidth > pageWidth / 4
            if (trailing != null && stacked) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Row(
                        modifier = Modifier.onSizeChanged { actionsWidth = with(density) { it.width.toDp() } },
                        horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        trailing()
                    }
                }
                Spacer(Modifier.height(dimens.s))
            }
            Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (trailing != null && !stacked) {
                                maxOf(actionsWidth, dimens.l)
                            } else {
                                dimens.l
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (trailing != null && !stacked) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .onSizeChanged { actionsWidth = with(density) { it.width.toDp() } },
                        horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}

/**
 * The shared screen frame.
 *
 * Every destination in PULSE is a [PulseScaffold]: one background, one optional bottom bar, and
 * content that is handed its padding rather than guessing at it. The header is **not** part of it —
 * the shell owns the breadcrumb bar, and a page owns its header ([PulsePageHeader]) — which is what
 * makes eighteen destinations share one gutter and one hierarchy without any of them owning either.
 *
 * The frame also owns the **measure**: the page's content is capped at
 * [com.pulse.intervalcoach.ui.theme.PulseLayout.contentMaxWidth] and centred inside it, so a
 * destination on a desktop is the same page as that destination on a phone — same gutter, same
 * measure, same place for the title — rather than the same content stretched across a monitor.
 */
@Composable
fun PulseScaffold(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /** The widest the page's content may get. `null` uses the responsive measure. */
    maxContentWidth: Dp? = null,
    /** True for the immersive screens, which fill the whole window instead of the measure. */
    fullWidth: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalPulseColors.current
    val layout = LocalPulseLayout.current
    val measure: Dp? = when {
        fullWidth -> null
        maxContentWidth != null -> maxContentWidth
        else -> layout.contentMaxWidth
    }
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        bottomBar = {
            // The bar shares the measure: a page's action button sits in the same column as the
            // page it belongs to, instead of floating at the edge of a wide window.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (measure != null) {
                                Modifier.widthIn(max = measure)
                            } else {
                                Modifier
                            },
                        )
                        .fillMaxWidth(),
                ) {
                    bottomBar()
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (measure != null) {
                            Modifier.widthIn(max = measure)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                // The frame has already applied the scaffold's own padding, so the page is handed
                // an empty one: a screen that applied it a second time would double the inset.
                content(PaddingValues(0.dp, 0.dp, 0.dp, 0.dp))
            }
        }
    }
}

/**
 * Composites a colour wash: `overlay` at a small, fixed alpha over `base`. Used instead of
 * `copy(alpha = …)` on a surface so the result is a real pre-composited colour — the same trick the
 * palette uses for phase tints, and the reason a washed card's text contrast is not an estimate.
 */
private fun wash(base: Color, overlay: Color, strength: Float = 0.06f): Color =
    Color(
        red = base.red * (1 - strength) + overlay.red * strength,
        green = base.green * (1 - strength) + overlay.green * strength,
        blue = base.blue * (1 - strength) + overlay.blue * strength,
        alpha = base.alpha,
    )

