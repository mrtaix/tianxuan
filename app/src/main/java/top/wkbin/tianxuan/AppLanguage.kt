package top.wkbin.tianxuan

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Initialize the fork's default once, after AppCompat has restored saved locales. */
internal fun initializeAppLanguage(context: Context) {
    val preferences = context.getSharedPreferences("ctharness.locale", Context.MODE_PRIVATE)
    if (preferences.getBoolean("initialized", false)) return

    // Mark first so choosing the system language later is not mistaken for first launch.
    preferences.edit().putBoolean("initialized", true).apply()
    if (AppCompatDelegate.getApplicationLocales().isEmpty) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi"))
    }
}
