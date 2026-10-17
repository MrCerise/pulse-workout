package com.mrcerise.intervalcoach.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mrcerise.intervalcoach.data.AppLanguage
import java.util.Locale

/**
 * The in-app language choice, applied to the UI.
 *
 * The app has no AppCompat dependency, so the platform's canned per-app locale plumbing
 * (`AppCompatDelegate.setApplicationLocales`) is not available; the choice is applied where the app
 * actually draws its text instead:
 *
 *  - [localizedContext] wraps a context so `context.getString(…)` and Compose's `stringResource(…)`
 *    resolve against the selected locale. Providing it through [Localized] covers every screen, the
 *    dialogs and the content descriptions in one place, and the change is visible on the next frame —
 *    no activity recreation, no restart.
 *  - [applySystemLocales] additionally tells the platform (API 33+) which language the app speaks, so
 *    the surfaces the app does not compose itself — home-screen widgets and the notification that keeps
 *    a session alive — agree with the screens. Below 33 there is no per-app override to hand over, and
 *    those surfaces stay in the system language.
 *
 * The app deliberately does not declare `android:localeConfig`. That attribute is the platform's own
 * per-app-language mechanism — it buys a *Settings → Apps → Languages* entry and asks the system to
 * store the value — and it exists only from API 33, while the choice has to work from API 26 and be
 * offered by the app's own settings screen. The DataStore entry is therefore the record, and the
 * platform store is told about it so the two never disagree about what the app draws.
 *
 * Translated tables are packaged for every Crowdin-approved locale (see docs/TRANSLATING.md), so a
 * language with no strings of its own simply falls back to English rather than showing blanks.
 */
object AppLocale {

    /** The [Locale] the choice maps to, or `null` when the app follows the device. */
    fun localeFor(language: AppLanguage): Locale? =
        language.tag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)

    /**
     * A context whose resources are [language]'s.
     *
     * Returns [base] untouched for [AppLanguage.SYSTEM]: the default must not cost an allocation, a
     * configuration copy or a subtly different `Resources` instance for every reader.
     */
    fun localizedContext(base: Context, language: AppLanguage): Context {
        val locale = localeFor(language) ?: return base
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    /**
     * Hands the choice to the platform's per-app locale store (`android.app.LocaleManager`, API 33+,
     * ignored below).
     *
     * The platform documents this as causing a configuration change — and possibly an activity lifecycle
     * event — in the calling app, which is why the caller writes the preference *first*: the stored
     * choice is what the app re-applies on its own, and the composition never depends on this call. An
     * empty list is the platform's own way back to "follow the system".
     *
     * A failure here is not worth failing a settings tap over — the in-app strings are already localized
     * by [localizedContext], which is the part the user is looking at.
     */
    fun applySystemLocales(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = runCatching { context.getSystemService(LocaleManager::class.java) }.getOrNull()
            ?: return
        val locales = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        // Only write when the platform disagrees. This runs at every app start (see PulseApp), and a
        // redundant write would mean a configuration change in the calling app for no reason at all.
        val stored = runCatching { manager.applicationLocales }.getOrDefault(LocaleList.getEmptyLocaleList())
        if (stored == locales) return
        runCatching { manager.setApplicationLocales(locales) }
    }
}

/**
 * Scopes [LocalContext] to [language] for [content].
 *
 * `stringResource` reads `LocalContext` in the Compose version this app pins, which is what makes the
 * override reach every string in the tree. A Compose upgrade that moves resource lookups onto
 * `LocalResources` must provide that local here too, or the selector silently stops relabelling text.
 */
@Composable
fun Localized(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    // Keep the composition shape identical across language changes: branching on the language
    // here would dispose and recreate the whole subtree (and lose saved state such as the
    // welcome tour's current step) whenever the user picks a different language.
    val context = remember(base, language) { AppLocale.localizedContext(base, language) }
    CompositionLocalProvider(LocalContext provides context, content = content)
}
