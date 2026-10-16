package com.mrcerise.intervalcoach.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors

/** Shared brand mark. The monochrome tint follows the surface; the supplied geometry never changes. */
@Composable
fun PulseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalPulseColors.current.textPrimary,
) {
    Icon(
        painter = painterResource(R.drawable.ic_pulse),
        contentDescription = null, // The adjacent wordmark / page title supplies the name.
        tint = tint,
        modifier = modifier.size(size),
    )
}
