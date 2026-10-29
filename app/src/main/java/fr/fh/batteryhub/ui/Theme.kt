package fr.fh.batteryhub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VertBatterie = Color(0xFF2ECC8F)
val OrangeBatterie = Color(0xFFF0A63A)
val RougeBatterie = Color(0xFFE5534B)
val BleuAccent = Color(0xFF4C9AFF)

private val Sombre = darkColorScheme(
    primary = BleuAccent,
    secondary = VertBatterie,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1C2330),
    onBackground = Color(0xFFE8EDF4),
    onSurface = Color(0xFFE8EDF4),
    onSurfaceVariant = Color(0xFF8B97A8),
    error = RougeBatterie,
)

private val Clair = lightColorScheme(
    primary = Color(0xFF1A63D8),
    secondary = Color(0xFF12915F),
    background = Color(0xFFEEF1F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F6FA),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    error = Color(0xFFC0392B),
)

@Composable
fun ThemeBatteryHub(
    sombre: Boolean = isSystemInDarkTheme(),
    contenu: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (sombre) Sombre else Clair,
        typography = Typography(),
        content = contenu,
    )
}

fun couleurNiveau(categorie: String): Color = when (categorie) {
    "ko" -> RougeBatterie
    "warn" -> OrangeBatterie
    else -> VertBatterie
}
