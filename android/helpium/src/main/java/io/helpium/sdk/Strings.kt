package io.helpium.sdk

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The chat's texts follow the forced locale, else the phone. */
internal object Strings {
    var forcedLocale: String? = null

    fun locale(context: Context): String {
        forcedLocale?.let { return it }
        val language = context.resources.configuration.let {
            if (android.os.Build.VERSION.SDK_INT >= 24) it.locales[0].language else @Suppress("DEPRECATION") it.locale.language
        }
        return if (language == "es") "es" else "en"
    }

    fun localized(context: Context): Context {
        val forced = forcedLocale ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(forced))
        return context.createConfigurationContext(config)
    }

    fun get(context: Context, id: Int, vararg args: Any): String = localized(context).getString(id, *args)
}
