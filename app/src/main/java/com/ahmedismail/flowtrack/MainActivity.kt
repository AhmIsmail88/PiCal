package com.ahmedismail.flowtrack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ahmedismail.flowtrack.ui.nav.FlowTrackNavHost
import com.ahmedismail.flowtrack.ui.theme.CardWhite
import com.ahmedismail.flowtrack.ui.theme.FlowTrackTheme
import com.ahmedismail.flowtrack.ui.theme.Navy
import com.ahmedismail.flowtrack.util.LocaleHelper

/**
 * Caps how far the system's font-scale accessibility setting can inflate
 * text. FlowTrack's screens are dense, aligned readouts (stat cards, the
 * joint rail, table-style rows) built to the spec's exact type scale — on a
 * device/emulator with "largest" font size enabled, letting sp scale freely
 * blows those layouts up far past what they were designed for. 1.15x still
 * gives a modest accessibility bump without breaking row alignment.
 */
private const val MAX_FONT_SCALE = 1.15f

private const val LANGUAGE_PREFS = "display"
private const val LANGUAGE_KEY = "language"
private const val DEFAULT_LANGUAGE = "en"

class MainActivity : ComponentActivity() {
    /**
     * The in-app language is applied to the Activity's own context, not only
     * through CompositionLocals. Compose composes dialog content in a window
     * composition that re-provides its own LocalContext/LocalConfiguration from
     * the Activity context, so a CompositionLocal-only locale left every dialog
     * — delete confirmations, the date picker, the photo-source chooser —
     * rendering in the device language while the screens behind it were Arabic.
     * Attaching it here means app and framework strings, layout direction and
     * Java date formatting inside dialogs all follow the chosen language.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, savedLanguageTag(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Navy.toArgb()),
            navigationBarStyle = SystemBarStyle.light(CardWhite.toArgb(), CardWhite.toArgb())
        )
        setContent {
            val preferences = remember { getSharedPreferences(LANGUAGE_PREFS, MODE_PRIVATE) }
            val languageTag = remember { preferences.getString(LANGUAGE_KEY, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE }

            val baseDensity = LocalDensity.current
            val clampedDensity = remember(baseDensity) {
                Density(density = baseDensity.density, fontScale = baseDensity.fontScale.coerceAtMost(MAX_FONT_SCALE))
            }

            CompositionLocalProvider(LocalDensity provides clampedDensity) {
                FlowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        FlowTrackNavHost(
                            app = application as FlowTrackApplication,
                            onToggleLanguage = {
                                val next = if (languageTag == "ar") "en" else "ar"
                                preferences.edit().putString(LANGUAGE_KEY, next).apply()
                                // The locale now lives in the Activity context, so the
                                // Activity is recreated to apply it. rememberSaveable
                                // keeps the current screen's input and position intact.
                                recreate()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun savedLanguageTag(context: Context): String =
        context.getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
}
