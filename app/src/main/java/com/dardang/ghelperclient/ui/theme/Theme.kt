package com.dardang.ghelperclient.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


var MetricBorderColor: Color = Color.White
    private set


var ContainerColor = Color(30, 33, 42)
    private set
var ContainerColor2 = Color(22, 25, 32)
    private set

val SensorColor = Color(46, 128, 194)

val SensorDarkColor = Color(10, 119, 206, 255)
val SensorHeavyDarkColor = Color(0, 80, 141, 255)


private val DarkColorScheme = darkColorScheme(
    primary = SensorColor,
    secondary = SensorDarkColor,
    tertiary = SensorHeavyDarkColor,
    background = Color.Black,
    surface = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = SensorColor,
    secondary = SensorDarkColor,
    tertiary = SensorHeavyDarkColor,
    background = Color.White,
    surface = Color.White,
    //onSurface = SensorHeavyDarkColor,
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)


@Composable
fun GHelperClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> {

            ContainerColor = Color(30, 33, 42)
            ContainerColor2 = Color(22, 25, 32)

            MetricBorderColor = Color.White.copy(alpha = .1f)
            DarkColorScheme
        }

        else -> {
            ContainerColor = Color(235, 235, 235)
            ContainerColor2 = Color(245, 245, 245)

            MetricBorderColor = Color.Black.copy(alpha = .1f)
            LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

