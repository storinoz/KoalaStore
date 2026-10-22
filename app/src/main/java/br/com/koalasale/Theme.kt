package br.com.koalasale

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) { DARK("Escuro"), LIGHT("Claro"), SYSTEM("Automático (sistema)") }

object ThemePreferences {
    fun read(context: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", ThemeMode.DARK.name)!!)
    }.getOrDefault(ThemeMode.DARK)
    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF14684D), onPrimary = Color.White,
    primaryContainer = Color(0xFFCEEEDC), onPrimaryContainer = Color(0xFF0A3C2A),
    secondary = Color(0xFF56625C), background = Color(0xFFF2F4F3), surface = Color(0xFFFDFEFD),
    surfaceVariant = Color(0xFFE0E5E2), onSurface = Color(0xFF1D2420), onSurfaceVariant = Color(0xFF47534C)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF85D9AA), onPrimary = Color(0xFF073723),
    primaryContainer = Color(0xFF214F38), onPrimaryContainer = Color(0xFFB9F1CD),
    secondary = Color(0xFFB1BCB5), background = Color(0xFF141816), surface = Color(0xFF1D2420),
    surfaceVariant = Color(0xFF323C35), onSurface = Color(0xFFE3EAE5), onSurfaceVariant = Color(0xFFBFCAC2)
)

@Composable fun KoalaStoreTheme(content: @Composable (ThemeMode, (ThemeMode) -> Unit) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ThemePreferences.read(context)) }
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        content(mode) { chosen -> mode = chosen; ThemePreferences.write(context, chosen) }
    }
}
