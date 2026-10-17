package com.mrcerise.intervalcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrcerise.intervalcoach.data.UserPreferences
import com.mrcerise.intervalcoach.locale.Localized
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.PulseTheme

/** Offline policy, directly accessible from Settings and Health Connect without onboarding. */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PulseApp).container
        setContent {
            val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            Localized(prefs.appLanguage) {
                PulseTheme(
                    themeMode = prefs.themeMode,
                    dynamicColor = prefs.dynamicColor,
                    highContrast = prefs.highContrast,
                    reducedMotion = prefs.reducedMotion,
                ) {
                    val titles = stringArrayResource(R.array.privacy_section_titles)
                    val bodies = stringArrayResource(R.array.privacy_section_bodies)
                    val dimens = LocalPulseDimens.current
                    val colors = LocalPulseColors.current
                    PulseScaffold { padding ->
                        LazyColumn(
                            modifier = Modifier.padding(padding).windowInsetsPadding(WindowInsets.safeDrawing),
                            contentPadding = PaddingValues(dimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
                        ) {
                            item {
                                PulsePageHeader(
                                    title = stringResource(R.string.privacy_policy_title),
                                    onBack = { finish() },
                                    backLabel = stringResource(R.string.privacy_back),
                                )
                            }
                            item { Text(stringResource(R.string.privacy_updated), color = colors.textSecondary) }
                            titles.indices.forEach { index ->
                                item { SectionHeader(titles[index]) }
                                item {
                                    Text(bodies[index], style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
