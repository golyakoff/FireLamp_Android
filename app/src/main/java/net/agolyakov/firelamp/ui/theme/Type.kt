package net.agolyakov.firelamp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import net.agolyakov.firelamp.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val bodyFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Roboto Sans"),
        fontProvider = provider,
    )
)

val displayFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Montserrat"),
        fontProvider = provider,
    )
)

// Default Material 3 typography values
val baseline = Typography()

val AppTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily, ),
    titleLarge = baseline.titleLarge.copy(fontFamily = bodyFontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = bodyFontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = bodyFontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
)

val monoFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Azeret Mono"),
        fontProvider = provider,
    )
)

val Typography.timeDisplayLarge: TextStyle
    get() = this.displayLarge.copy(fontFamily = monoFontFamily)

val Typography.timeDisplayMedium: TextStyle
    get() = this.displayMedium.copy(fontFamily = monoFontFamily)

val Typography.timeDisplaySmall: TextStyle
    get() = this.displaySmall.copy(fontFamily = monoFontFamily)

val Typography.timeHeadlineLarge: TextStyle
    get() = this.headlineLarge.copy(fontFamily = monoFontFamily)

val Typography.timeHeadlineMedium: TextStyle
    get() = this.headlineMedium.copy(fontFamily = monoFontFamily)

val Typography.timeHeadlineSmall: TextStyle
    get() = this.headlineSmall.copy(fontFamily = monoFontFamily)

val Typography.timeTitleLarge: TextStyle
    get() = this.titleLarge.copy(fontFamily = monoFontFamily)

val Typography.timeTitleMedium: TextStyle
    get() = this.titleMedium.copy(fontFamily = monoFontFamily)

val Typography.timeTitleSmall: TextStyle
    get() = this.titleSmall.copy(fontFamily = monoFontFamily)