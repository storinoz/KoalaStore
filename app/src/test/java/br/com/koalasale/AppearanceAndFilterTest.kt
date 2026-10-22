package br.com.koalasale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppearanceAndFilterTest {
    @Test fun defaultDarkAndSelectedModeSurvivesNewRead() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(ThemeMode.DARK, ThemePreferences.read(context))
        ThemePreferences.write(context, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, ThemePreferences.read(context))
        ThemePreferences.write(context, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.read(context))
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun monthFilterRespectsMonthYearAndUndatedQuotes() {
        assertTrue(Rules.inMonth("2026-10-03", 202610))
        assertFalse(Rules.inMonth("2026-11-03", 202610))
        assertFalse(Rules.inMonth("2025-10-03", 202610))
        assertFalse(Rules.inMonth(null, 202610))
        assertTrue(Rules.inMonth(null, 0))
        assertTrue(Rules.inMonth("2026-10-03", 0))
        assertTrue(Rules.inMonth(null, -1))
        assertFalse(Rules.inMonth("2026-10-03", -1))
    }
}
