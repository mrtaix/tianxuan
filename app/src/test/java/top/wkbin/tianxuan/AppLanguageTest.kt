package top.wkbin.tianxuan

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, manifest = Config.NONE)
class AppLanguageTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun resetPreferences() {
        context.getSharedPreferences("ctharness.locale", Context.MODE_PRIVATE)
            .edit().clear().commit()
        resetLocales()
    }

    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun freshInstallationStartsInVietnamese() {
        initializeAppLanguage(context)
        assertEquals("vi", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun existingExplicitLanguageIsNotOverwritten() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        initializeAppLanguage(context)
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun followSystemSurvivesTheNextInitialization() {
        initializeAppLanguage(context)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        initializeAppLanguage(context)
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test
    fun laterChineseChoiceIsNotReset() {
        initializeAppLanguage(context)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
        initializeAppLanguage(context)
        assertEquals("zh-CN", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
}
