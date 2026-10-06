package com.ahmedismail.flowtrack.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Spec §2.2: fully bilingual app with an in-app language switch (not just
 * device-locale driven). Wraps the Context with locale-adjusted Resources so
 * stringResource() picks up values/values-ar transparently.
 *
 * IMPORTANT: this must stay a [ContextWrapper] around the *original* Activity
 * context (never a fresh Context from createConfigurationContext() used
 * directly as LocalContext). A raw createConfigurationContext() result is not
 * a ContextWrapper over the Activity and breaks any code that walks the
 * context chain looking for the Activity — notably
 * rememberLauncherForActivityResult (camera/gallery pickers), which throws
 * "No Activity was found on which the result api can be started" the moment
 * it's used, crashing Add Progress Entry specifically. Only getResources()
 * is overridden here; everything else (startActivityForResult,
 * findViewTreeLifecycleOwner, etc.) still resolves through to the real
 * Activity via ContextWrapper.baseContext.
 */
object LocaleHelper {
    fun wrap(context: Context, languageTag: String): Context {
        val locale = Locale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val localizedResources = context.createConfigurationContext(config).resources
        return object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
        }
    }
}
