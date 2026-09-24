package com.example.nightwatchprotocol.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R

val NightWatchDisplayFontFamily = FontFamily(
    Font(R.font.iceland_regular, FontWeight.Normal),
)

val NightWatchBodyFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val NightWatchDisplayStyle = TextStyle(
    fontFamily = NightWatchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 40.sp,
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
)
