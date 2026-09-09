package me.zippert.dialoglite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6F5C),
    secondary = Color(0xFF4C6B60),
    tertiary = Color(0xFF7A5C2E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6BF),
    secondary = Color(0xFFB3CCC1),
    tertiary = Color(0xFFE5C48F),
)

/** Cores semanticas usadas em saldo/delta, coerentes nos dois temas. */
object BalanceColors {
    val positive: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7FD1A0) else Color(0xFF1B6B3A)
    val negative: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF09A9A) else Color(0xFFA32C2C)
}

@Composable
fun DiaLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
