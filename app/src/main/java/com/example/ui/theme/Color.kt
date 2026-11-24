package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object AdaColors {
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF0F172A)
    val SurfaceElevated = Color(0xFF131D33)
    val SurfaceCard = Color(0xCC0F172A)
    val SurfaceDeep = Color(0xE6070B14)
    val Border = Color(0xFF1E293B)
    val BorderStrong = Color(0xFF334155)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)
    val TextSubtle = Color(0xFF64748B)

    // ADA Alianza palette
    val SkyPrimary = Color(0xFF38BDF8)
    val IndigoSecondary = Color(0xFF6366F1)
    val PinkAccent = Color(0xFFF472B6)

    // Accent palette
    val AmberAccent = Color(0xFFFBBF24)

    // Semantic feedback
    val EmeraldCalm = Color(0xFF34D399)
    val EmeraldSolid = Color(0xFF10B981)
    val RoseAlert = Color(0xFFF43F5E)
    val RoseSoft = Color(0xFFFDA4AF)
    val VioletAccent = Color(0xFFA78BFA)
}

fun parseHexColor(hex: String, fallback: Color = AdaColors.SkyPrimary): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = when (clean.length) {
            6 -> 0xFF000000L or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return fallback
        }
        Color(value.toInt())
    } catch (_: Exception) {
        fallback
    }
}
