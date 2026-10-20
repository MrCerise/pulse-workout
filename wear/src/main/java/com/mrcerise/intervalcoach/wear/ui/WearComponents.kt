package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors

/**
 * Every scrolling watch screen: a Material 3 [ScreenScaffold] around a [TransformingLazyColumn],
 * so items morph at the round edges, the scroll indicator and time text behave the same on every
 * screen, and rotary input scrolls without any per-screen wiring. [content] receives the
 * transformation spec its items pass to [transformed] / [SurfaceTransformation].
 */
@Composable
fun PulseListScreen(
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val column: @Composable BoxScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
        TransformingLazyColumn(
            state = state,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            content(spec)
        }
    }
    if (edgeButton != null) {
        ScreenScaffold(scrollState = state, edgeButton = edgeButton, content = column)
    } else {
        ScreenScaffold(scrollState = state, content = column)
    }
}

/** Full-width item modifier that follows the column's height morphing. */
fun Modifier.transformed(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
    fillMaxWidth().transformedHeight(scope, spec)

/** A screen title, morphing with the list like every other item. */
@Composable
fun TransformingLazyColumnItemScope.ScreenHeader(text: String, spec: TransformationSpec) {
    ListHeader(
        modifier = Modifier.transformed(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(text)
    }
}

/** A short centred caption between list items: hints, states, the about line. */
@Composable
fun TransformingLazyColumnItemScope.Caption(
    text: String,
    spec: TransformationSpec,
    color: Color = LocalWearColors.current.textMuted,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.transformed(this, spec).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
